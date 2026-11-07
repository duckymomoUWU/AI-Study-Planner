package com.studyplanner.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Success envelope: {"data": ...} and, for paginated lists, a "meta" object. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Meta meta) {
    public record Meta(int page, int limit, long totalItems, int totalPages) {}

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<List<T>> page(List<T> items, int page, int limit, long totalItems) {
        int totalPages = (int) ((totalItems + limit - 1) / limit);
        return new ApiResponse<>(items, new Meta(page, limit, totalItems, totalPages));
    }
}
