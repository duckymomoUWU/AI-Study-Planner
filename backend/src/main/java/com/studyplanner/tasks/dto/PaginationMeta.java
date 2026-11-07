package com.studyplanner.tasks.dto;

public record PaginationMeta(
        int page,
        int limit,
        long totalItems,
        long totalPages
) {
    public static PaginationMeta of(int page, int limit, long totalItems) {
        long totalPages = limit > 0 ? (totalItems + limit - 1) / limit : 0;
        return new PaginationMeta(page, limit, totalItems, totalPages);
    }
}
