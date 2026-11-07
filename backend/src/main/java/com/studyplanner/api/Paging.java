package com.studyplanner.api;

import java.util.ArrayList;
import java.util.List;

/** Validated page and limit query parameters: page defaults to 1, limit to 20 (max 100). */
public record Paging(int page, int limit) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public static Paging of(Integer page, Integer limit) {
        int p = page == null ? 1 : page;
        int l = limit == null ? DEFAULT_LIMIT : limit;
        List<ApiErrorResponse.ErrorDetail> errors = new ArrayList<>();
        if (p < 1) {
            errors.add(new ApiErrorResponse.ErrorDetail("page", "out_of_range", "page phải từ 1 trở lên."));
        }
        if (l < 1 || l > MAX_LIMIT) {
            errors.add(new ApiErrorResponse.ErrorDetail("limit", "out_of_range",
                    "limit phải từ 1 đến " + MAX_LIMIT + "."));
        }
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
        return new Paging(p, l);
    }

    public long offset() {
        return (long) (page - 1) * limit;
    }
}
