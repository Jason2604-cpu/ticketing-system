package com.konohahelpdesk.dto.kb;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** A page of KB rows. Our own shape rather than Spring's Page, whose JSON isn't a stable contract. */
public record KbPage<T>(List<T> items, long totalElements, int totalPages, int page, int size) {

    public static <S, T> KbPage<T> of(Page<S> page, Function<S, T> mapper) {
        return new KbPage<>(page.getContent().stream().map(mapper).toList(),
            page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
