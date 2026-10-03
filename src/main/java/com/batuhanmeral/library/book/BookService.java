package com.batuhanmeral.library.book;

import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.author.AuthorRepository;
import com.batuhanmeral.library.book.dto.BookRequest;
import com.batuhanmeral.library.book.dto.BookResponse;
import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.category.CategoryRepository;
import com.batuhanmeral.library.common.error.DuplicateResourceException;
import com.batuhanmeral.library.common.error.ResourceNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
    private final BookMapper bookMapper;

    public List<BookResponse> findAll() {
        return bookRepository.findAll().stream().map(bookMapper::toResponse).toList();
    }

    public BookResponse findById(Long id) {
        return bookMapper.toResponse(getBook(id));
    }

    public List<BookResponse> findByAuthor(Long authorId) {
        if (!authorRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Author", authorId);
        }
        return bookRepository.findByAuthorId(authorId).stream()
                .map(bookMapper::toResponse)
                .toList();
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

    @Transactional
    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book", id);
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
