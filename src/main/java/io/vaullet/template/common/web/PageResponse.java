package io.vaullet.template.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable pagination envelope for list endpoints.
 *
 * <p>Returning Spring Data's {@code Page} straight from a controller is the most common accidental
 * API break in Spring: its JSON shape is an implementation detail that has changed across versions,
 * it serialises {@code Pageable} internals nobody asked for, and Boot logs a warning about exactly
 * this. A small record you own costs five lines and pins the contract forever.
 *
 * @param <T> element type of the current page
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {

    /** Wraps a {@code Page} of entities, mapping each element on the way out. */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<? super S, ? extends T> mapper) {
        Page<T> mapped = page.map(mapper);
        return new PageResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages(),
                mapped.isFirst(),
                mapped.isLast());
    }
}
