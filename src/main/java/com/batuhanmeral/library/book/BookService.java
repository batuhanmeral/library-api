package com.batuhanmeral.library.book;

import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.dto.BookPatchRequest;
import com.batuhanmeral.library.book.dto.BookRequest;
import com.batuhanmeral.library.book.dto.BookResponse;
import com.batuhanmeral.library.book.dto.BookSearchCriteria;
import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.category.CategoryRepository;
import com.batuhanmeral.library.common.audit.AuditAction;
import com.batuhanmeral.library.common.audit.Auditable;
import com.batuhanmeral.library.common.error.DuplicateResourceException;
import com.batuhanmeral.library.common.error.InvalidQueryParameterException;
import com.batuhanmeral.library.common.error.ResourceInUseException;
import com.batuhanmeral.library.common.error.ResourceNotFoundException;
import com.batuhanmeral.library.config.CacheNames;
import com.batuhanmeral.library.loan.LoanRepository;
import com.batuhanmeral.library.loan.LoanService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads run in a read-only transaction: the mapper touches the lazy author and categories, and with
 * open-in-view turned off the persistence context lives only as long as this transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final LoanRepository loanRepository;
    private final BookMapper bookMapper;

    public Page<BookResponse> search(BookSearchCriteria criteria, Pageable pageable) {
        if (criteria.yearFrom() != null
                && criteria.yearTo() != null
                && criteria.yearFrom() > criteria.yearTo()) {
            throw new InvalidQueryParameterException("yearFrom must not be greater than yearTo.");
        }
        return bookRepository
                .findAll(BookSpecifications.matching(criteria), pageable)
                .map(bookMapper::toResponse);
    }

    /**
     * Cached by id. A book that does not exist throws, and nothing is cached for it. The entry is a
     * BookResponse, not the Book entity: a detached entity with lazy associations would fail to
     * load them later, and the response is what the API returns anyway.
     */
    @Cacheable(cacheNames = CacheNames.BOOKS, key = "#id")
    public BookResponse findById(Long id) {
        return bookMapper.toResponse(getBook(id));
    }

    public Page<BookResponse> findByAuthor(Long authorId, Pageable pageable) {
        if (!authorRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Author", authorId);
        }
        return search(BookSearchCriteria.byAuthor(authorId), pageable);
    }

    /**
     * The cheap checks of {@link #create}, done before a slow call to Open Library: a taken ISBN or
     * an unknown author fails without it. create checks again; this is not a reservation.
     */
    public void checkNewBook(String isbn, Long authorId) {
        if (isbn != null && bookRepository.existsByIsbn(isbn)) {
            throw isbnTaken(isbn);
        }
        if (!authorRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Author", authorId);
        }
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        if (request.isbn() != null && bookRepository.existsByIsbn(request.isbn())) {
            throw isbnTaken(request.isbn());
        }
        Book book = bookMapper.toEntity(request);
        applyRelationsAndCopies(request, book);
        return bookMapper.toResponse(bookRepository.save(book));
    }

    @Auditable(action = AuditAction.BOOK_UPDATE, target = "#id")
    @CacheEvict(cacheNames = CacheNames.BOOKS, key = "#id")
    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = getBook(id);
        if (request.isbn() != null && bookRepository.existsByIsbnAndIdNot(request.isbn(), id)) {
            throw isbnTaken(request.isbn());
        }
        bookMapper.updateEntity(request, book);
        applyRelationsAndCopies(request, book);
        bookRepository.flush();
        return bookMapper.toResponse(book);
    }

    /** Changes only the fields that were sent; see {@link BookPatchRequest}. */
    @Auditable(action = AuditAction.BOOK_UPDATE, target = "#id")
    @CacheEvict(cacheNames = CacheNames.BOOKS, key = "#id")
    @Transactional
    public BookResponse patch(Long id, BookPatchRequest request) {
        Book book = getBook(id);
        if (request.isbn() != null && bookRepository.existsByIsbnAndIdNot(request.isbn(), id)) {
            throw isbnTaken(request.isbn());
        }
        bookMapper.patchEntity(request, book);
        if (request.authorId() != null) {
            book.setAuthor(getAuthor(request.authorId()));
        }
        if (request.categoryIds() != null) {
            book.replaceCategories(getCategories(request.categoryIds()));
        }
        if (request.totalCopies() != null) {
            book.changeTotalCopies(request.totalCopies());
        }
        bookRepository.flush();
        return bookMapper.toResponse(book);
    }

    /**
     * A book with loans cannot be deleted: the loans point to it (fk_loans_book) and are the
     * library's record of who had which book. Open loans get their own message.
     */
    @Auditable(action = AuditAction.BOOK_DELETE, target = "#id")
    @CacheEvict(cacheNames = CacheNames.BOOKS, key = "#id")
    @Transactional
    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book", id);
        }
        if (loanRepository.existsByBookIdAndStatusIn(id, LoanService.OPEN_STATUSES)) {
            throw new ResourceInUseException(
                    "Book with id %d has active loans and cannot be deleted.".formatted(id));
        }
        if (loanRepository.existsByBookId(id)) {
            throw new ResourceInUseException(
                    "Book with id %d has a loan history and cannot be deleted.".formatted(id));
        }
        // Hibernate deletes the book's rows in book_categories first: Book owns that table.
        bookRepository.deleteById(id);
    }

    private void applyRelationsAndCopies(BookRequest request, Book book) {
        // Setting the owning side is what writes author_id; Author.books is not touched.
        book.setAuthor(getAuthor(request.authorId()));
        book.replaceCategories(getCategories(request.categoryIds()));
        book.changeTotalCopies(request.totalCopies());
    }

    private Book getBook(Long id) {
        return bookRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
    }

    private Author getAuthor(Long id) {
        return authorRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author", id));
    }

    private Set<Category> getCategories(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        List<Category> found = categoryRepository.findAllById(ids);
        Set<Long> foundIds = found.stream().map(Category::getId).collect(Collectors.toSet());
        ids.stream()
                .filter(id -> !foundIds.contains(id))
                .sorted()
                .findFirst()
                .ifPresent(missing -> {
                    throw new ResourceNotFoundException("Category", missing);
                });
        return new HashSet<>(found);
    }

    private static DuplicateResourceException isbnTaken(String isbn) {
        return new DuplicateResourceException(
                "A book with ISBN '%s' already exists.".formatted(isbn));
    }
}
