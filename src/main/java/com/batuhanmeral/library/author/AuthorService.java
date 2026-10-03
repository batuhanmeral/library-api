package com.batuhanmeral.library.author;

import com.batuhanmeral.library.author.dto.AuthorRequest;
import com.batuhanmeral.library.author.dto.AuthorResponse;
import com.batuhanmeral.library.book.BookRepository;
import com.batuhanmeral.library.common.error.ResourceInUseException;
import com.batuhanmeral.library.common.error.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final AuthorMapper authorMapper;

    public List<AuthorResponse> findAll() {
        return authorRepository.findAll().stream().map(authorMapper::toResponse).toList();
    }

    public AuthorResponse findById(Long id) {
        return authorMapper.toResponse(getAuthor(id));
    }

    @Transactional
    public AuthorResponse create(AuthorRequest request) {
        Author saved = authorRepository.save(authorMapper.toEntity(request));
        return authorMapper.toResponse(saved);
    }

    @Transactional
    public AuthorResponse update(Long id, AuthorRequest request) {
        Author author = getAuthor(id);
        authorMapper.updateEntity(request, author);
        // Dirty checking saves the change; flush so updatedAt is set in the response.
        authorRepository.flush();
        return authorMapper.toResponse(author);
    }

    @Transactional
    public void delete(Long id) {
        if (!authorRepository.existsById(id)) {
            throw new ResourceNotFoundException("Author", id);
        }
        if (bookRepository.existsByAuthorId(id)) {
            throw new ResourceInUseException(
                    "Author with id %d has books and cannot be deleted.".formatted(id));
        }
        authorRepository.deleteById(id);
    }

    private Author getAuthor(Long id) {
        return authorRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author", id));
    }
}
