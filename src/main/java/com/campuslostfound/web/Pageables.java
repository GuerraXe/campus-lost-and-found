package com.campuslostfound.web;

import com.campuslostfound.web.error.Exceptions;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds a {@link Pageable} from raw request parameters with a hard cap on page size and a
 * strict allow-list of sortable fields, so a client cannot sort by an arbitrary column.
 */
public final class Pageables {

    private static final int MAX_SIZE = 100;

    private Pageables() {
    }

    public static Pageable of(Integer page, Integer size, String sort, Set<String> allowedSortFields,
                              String defaultSort) {
        return of(page, size, sort, allowedSortFields, defaultSort, Sort.Direction.DESC);
    }

    public static Pageable of(Integer page, Integer size, String sort, Set<String> allowedSortFields,
                              String defaultSort, Sort.Direction defaultDirection) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);

        String field = defaultSort;
        Sort.Direction dir = defaultDirection;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            field = parts[0].trim();
            if (!allowedSortFields.contains(field)) {
                throw new Exceptions.BadRequestException(
                        "Cannot sort by '" + field + "'. Allowed: " + allowedSortFields);
            }
            if (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")) {
                dir = Sort.Direction.ASC;
            }
        }
        return PageRequest.of(p, s, Sort.by(dir, field));
    }
}
