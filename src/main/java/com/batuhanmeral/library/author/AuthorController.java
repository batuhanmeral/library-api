package com.batuhanmeral.library.author;

import com.batuhanmeral.library.author.dto.AuthorRequest;
import com.batuhanmeral.library.author.dto.AuthorResponse;
import com.batuhanmeral.library.book.BookController;
import com.batuhanmeral.library.book.BookService;
import com.batuhanmeral.library.book.dto.BookResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Authors")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/authors")
public class AuthorController {

    private static final SortableFields SORTABLE =
            SortableFields.of("id", "name", "birthDate", "createdAt");

    private final AuthorService authorService;
    private final BookService bookService;

    @SecurityRequirements
    @GetMapping
    public PageResponse<AuthorResponse> findAll(
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(authorService.findAll(SORTABLE.check(pageable)));
    }

    @SecurityRequirements
    @GetMapping("/{id}")
    public AuthorResponse findById(@PathVariable Long id) {
        return authorService.findById(id);
    }

    @SecurityRequirements
    @GetMapping("/{id}/books")
    public PageResponse<BookResponse> findBooks(
            @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return PageResponse.from(
                bookService.findByAuthor(id, BookController.SORTABLE.check(pageable)));
    }

    @PostMapping
    public ResponseEntity<AuthorResponse> create(@Valid @RequestBody AuthorRequest request) {
        AuthorResponse created = authorService.create(request);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(created.id())
                        .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public AuthorResponse update(@PathVariable Long id, @Valid @RequestBody AuthorRequest request) {
        return authorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        authorService.delete(id);
    }
}
