package com.batuhanmeral.library.common.web;

import com.batuhanmeral.library.common.error.InvalidQueryParameterException;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * The fields a list endpoint can be sorted by. Spring Data turns {@code ?sort=x} into {@code ORDER
 * BY x} without checking it against the API: an unknown name fails only when the query runs, and a
 * path such as {@code author.biography} or {@code categories.name} sorts by data the endpoint does
 * not return or joins a collection. Only the listed names are accepted.
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
     * Rejects a sort by any field that is not listed, then adds the id as the last sort key. Rows
     * with the same title would otherwise come back in any order, and a row could show up on two
     * pages or on none.
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
