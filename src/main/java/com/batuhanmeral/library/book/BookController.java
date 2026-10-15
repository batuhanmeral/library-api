package com.batuhanmeral.library.book;

import com.batuhanmeral.library.book.dto.BookPatchRequest;
import com.batuhanmeral.library.book.dto.BookRequest;
import com.batuhanmeral.library.book.dto.BookResponse;
import com.batuhanmeral.library.book.dto.BookSearchCriteria;
import com.batuhanmeral.library.book.dto.NewBookRequest;
import com.batuhanmeral.library.common.web.PageResponse;
import com.batuhanmeral.library.common.web.SortableFields;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Books")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/books")
public class BookController {

    /** GET /authors/{id}/books de aynı sıralama kurallarını kullanır. */
    public static final SortableFields SORTABLE =
            SortableFields.of("id", "title", "publishedYear", "availableCopies", "createdAt");

    private final BookService bookService;
    private final BookCreationService bookCreationService;

    /** Filtreler query string'den bağlanır; {@code @Valid} onları gövde gibi kontrol eder. */
    @SecurityRequirements
    @GetMapping
    public PageResponse<BookResponse> search(
            @Valid @ParameterObject BookSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return PageResponse.from(bookService.search(criteria, SORTABLE.check(pageable)));
    }

    @SecurityRequirements
    @GetMapping("/{id}")
    public BookResponse findById(@PathVariable Long id) {
        return bookService.findById(id);
    }

    /** ISBN varsa eksik bilgiler Open Library'den gelir, başlık da boş bırakılabilir. */
    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody NewBookRequest request) {
        BookResponse created = bookCreationService.create(request);
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

    @PatchMapping("/{id}")
    public BookResponse patch(@PathVariable Long id, @Valid @RequestBody BookPatchRequest request) {
        return bookService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        bookService.delete(id);
    }
}
