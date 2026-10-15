package com.batuhanmeral.library.common.web;

import com.batuhanmeral.library.common.error.InvalidQueryParameterException;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Bir listenin sıralanabileceği alanlar. Spring Data {@code ?sort=x} değerini kontrol etmeden ORDER
 * BY yapar; sadece listedeki adlar kabul edilir.
 */
public final class SortableFields {

    private static final String TIE_BREAKER = "id";

    private final Set<String> names;
    private final String message;

    private SortableFields(Set<String> names) {
        this.names = Set.copyOf(names);
        this.message =
                "Cannot sort by this field. Sortable fields: %s."
                        .formatted(String.join(", ", new TreeSet<>(names)));
    }

    public static SortableFields of(String... names) {
        return new SortableFields(Set.of(names));
    }

    /**
     * Listede olmayan alanı reddeder ve en sona id ekler; yoksa aynı başlıklı satırlar iki sayfada
     * birden ya da hiçbirinde görünebilirdi.
     */
    public Pageable check(Pageable pageable) {
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!names.contains(order.getProperty())) {
                throw new InvalidQueryParameterException(message);
            }
        }
        if (sort.getOrderFor(TIE_BREAKER) != null) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by(TIE_BREAKER)));
    }
}
