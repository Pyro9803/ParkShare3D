package com.parkshare.shared.api;

import java.util.List;

import org.springframework.data.domain.Page;

public record PagedResponse<T>(
        List<T> content,
        int totalPages,
        long totalElements,
        int page,
        int size
) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize()
        );
    }
}
