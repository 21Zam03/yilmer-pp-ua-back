package com.zam.sunat_service.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Respuesta paginada estable para el front (no se serializa Page/PageImpl directamente,
 * cuyo formato JSON no está garantizado entre versiones de Spring Data).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

}
