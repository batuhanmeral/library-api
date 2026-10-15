package com.batuhanmeral.library.book;

import com.batuhanmeral.library.book.dto.BookRequest;
import com.batuhanmeral.library.book.dto.BookResponse;
import com.batuhanmeral.library.book.dto.NewBookRequest;
import com.batuhanmeral.library.book.openlibrary.OpenLibraryBook;
import com.batuhanmeral.library.book.openlibrary.OpenLibraryClient;
import com.batuhanmeral.library.common.audit.AuditAction;
import com.batuhanmeral.library.common.audit.Auditable;
import com.batuhanmeral.library.common.error.BusinessRuleException;
import com.batuhanmeral.library.common.error.ErrorCode;
import com.batuhanmeral.library.common.error.ExternalServiceUnavailableException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Kitap ekler; istekte eksik olan bilgileri önce Open Library'ye sorar.
 *
 * <p>Bilerek {@code @Transactional} yok: yavaş HTTP çağrısı transaction içinde havuzdan bağlantı
 * tutardı. Önce sorgu yapılır, sadece kayıt başka bean'deki BookService.create içinde
 * transaction'dır. İstekteki değerler kazanır; başlık yoksa bilinmeyen ISBN 422, ulaşılamayan Open
 * Library 503 olur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookCreationService {

    private final OpenLibraryClient openLibraryClient;
    private final BookService bookService;

    @Auditable(action = AuditAction.BOOK_CREATE, target = "#result?.id()")
    public BookResponse create(NewBookRequest request) {
        OpenLibraryBook found =
                request.canBeCompleted()
                        ? lookUp(request)
                        : new OpenLibraryBook(null, null, null, null);
        String title = request.hasTitle() ? request.title() : found.title();
        if (title == null) {
            throw isbnNotFound(request.isbn());
        }
        return bookService.create(
                new BookRequest(
                        request.isbn(),
                        title,
                        firstNonNull(request.description(), found.description()),
                        firstNonNull(request.publishedYear(), found.publishedYear()),
                        firstNonNull(request.pageCount(), found.pageCount()),
                        request.totalCopies(),
                        request.authorId(),
                        request.categoryIds()));
    }

    private OpenLibraryBook lookUp(NewBookRequest request) {
        String isbn = request.isbn();
        // Zaten düşecek bir istek (alınmış ISBN, bilinmeyen yazar) dış çağrıya mal olmasın.
        bookService.checkNewBook(isbn, request.authorId());
        Optional<OpenLibraryBook> found;
        try {
            found = openLibraryClient.findByIsbn(isbn);
        } catch (RestClientException | InvocationRejectedException e) {
            if (!request.hasTitle()) {
                log.warn("Open Library lookup for ISBN {} failed ({})", isbn, name(e));
                throw new ExternalServiceUnavailableException(
                        "Open Library could not be reached. Send the title, or try again later.");
            }
            log.warn(
                    "Open Library lookup for ISBN {} failed ({}); saving the book as sent",
                    isbn,
                    name(e));
            return new OpenLibraryBook(null, null, null, null);
        }
        if (found.isEmpty()) {
            if (!request.hasTitle()) {
                throw isbnNotFound(isbn);
            }
            log.warn("Open Library has no book with ISBN {}; saving the book as sent", isbn);
        }
        return found.orElse(new OpenLibraryBook(null, null, null, null));
    }

    /** ISBN {@code @Pattern}'dan geçti (rakam ve X), mesaja ve log'a yazmak güvenli. */
    private static BusinessRuleException isbnNotFound(String isbn) {
        return new BusinessRuleException(
                ErrorCode.ISBN_NOT_FOUND,
                "Open Library has no book with ISBN %s. Send the title.".formatted(isbn));
    }

    private static String name(Exception e) {
        return e.getClass().getSimpleName();
    }

    private static <T> T firstNonNull(T fromRequest, T fromOpenLibrary) {
        return fromRequest != null ? fromRequest : fromOpenLibrary;
    }
}
