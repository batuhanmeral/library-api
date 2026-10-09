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
 * Adds a book, first asking Open Library for the details the request leaves out.
 *
 * <p>This class has no {@code @Transactional} on purpose. The HTTP call may take seconds (up to the
 * timeouts); inside a transaction it would hold a database connection from the pool all that time,
 * and with the pool's ten connections, ten slow lookups would stop every other request. So the
 * lookup runs first, without a transaction, and only the save is a transaction, in
 * BookService.create. That method is in another bean: called from here it goes through the proxy. A
 * {@code @Transactional} method of this class called with {@code this} would not start a
 * transaction at all (self-invocation, step 6).
 *
 * <p>Values sent in the request win; Open Library only fills in the gaps. When the request has a
 * title, a failed lookup does not stop the book from being saved. Without a title, the lookup is
 * the only source of one: an unknown ISBN is a 422, an unreachable Open Library a 503.
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
        // A request that would fail anyway (taken ISBN, unknown author) must not cost a call.
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

    /**
     * The ISBN passed {@code @Pattern} (digits and X only), so it is safe in a message and a log.
     */
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
