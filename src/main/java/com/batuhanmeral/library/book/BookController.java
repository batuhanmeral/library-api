package com.batuhanmeral.library.book;

import com.batuhanmeral.library.book.dto.BookRequest;
import com.batuhanmeral.library.book.dto.BookResponse;
import com.batuhanmeral.library.book.dto.BookSearchCriteria;
import com.batuhanmeral.library.common.web.PageResponse;
import com.batuhanmeral.library.common.web.SortableFields;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/books")
public class BookController {

    /** Also used by GET /authors/{id}/books, which returns the same book representation. */
    public static final SortableFields SORTABLE =
            SortableFields.of("id", "title", "publishedYear", "availableCopies", "createdAt");

    private final BookService bookService;

    /**
     * The criteria record is bound from the query string (page, size and sort go to the pageable).
     * {@code @Valid} checks it the same way as a request body.
     */
    @GetMapping
    public PageResponse<BookResponse> search(
            @Valid BookSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return PageResponse.from(bookService.search(criteria, SORTABLE.check(pageable)));
    }

    @GetMapping("/{id}")
    public BookResponse findById(@PathVariable Long id) {
        return bookService.findById(id);
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(created.id())
                        .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        bookService.delete(id);
    }
}
