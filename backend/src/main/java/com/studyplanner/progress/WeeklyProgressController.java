package com.studyplanner.progress;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.api.ApiResponse;
import com.studyplanner.api.RequestValidationException;
import com.studyplanner.api.UnauthenticatedException;
import com.studyplanner.auth.AppUserPrincipal;

/**
 * GET /api/v1/progress/weekly?date=YYYY-MM-DD: progress of the Monday-to-Sunday week that contains the date
 * (the current week when no date is given). Read only; the owner always comes from the authenticated principal.
 * Responses and errors use the shared envelope and {@code ApiExceptionHandler} (docs/backend-api-rules.md).
 */
@RestController
@RequestMapping("/api/v1/progress")
public class WeeklyProgressController {
    private final WeeklyProgressService service;

    public WeeklyProgressController(WeeklyProgressService service) {
        this.service = service;
    }

    @GetMapping("/weekly")
    public ApiResponse<WeeklyProgressService.WeeklyProgress> weekly(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String date) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        LocalDate day = null;
        if (date != null && !date.isBlank()) {
            try {
                day = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                throw invalidDate("invalid_date", "Ngày phải có dạng YYYY-MM-DD.");
            }
        }
        try {
            return ApiResponse.of(service.getWeek(principal.id(), day));
        } catch (WeeklyProgressService.DateOutOfRangeException e) {
            throw invalidDate("out_of_range", "Ngày phải nằm trong khoảng từ năm 2000 đến năm 2100.");
        }
    }

    private static RequestValidationException invalidDate(String issue, String message) {
        return new RequestValidationException(List.of(new ErrorDetail("date", issue, message)));
    }
}
