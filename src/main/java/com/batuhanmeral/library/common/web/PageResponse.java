package com.batuhanmeral.library.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of a list, as the API returns it. Spring Data's {@code PageImpl} is not returned
 * directly: its JSON shape is an implementation detail that has changed between versions, and it
 * carries fields clients do not need ({@code pageable}, {@code sort}, {@code empty}, ...).
 *
 * @param page zero-based page number
 * @param size the page size that was used, after the server's limit was applied
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
