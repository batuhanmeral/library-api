package com.batuhanmeral.library.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * API'nin döndüğü bir sayfa. {@code PageImpl} doğrudan dönmez: JSON biçimi sürümler arasında
 * değişti ve gereksiz alanlar taşır.
 *
 * @param page sıfırdan başlayan sayfa numarası
 * @param size sunucunun sınırı uygulandıktan sonraki sayfa boyu
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
