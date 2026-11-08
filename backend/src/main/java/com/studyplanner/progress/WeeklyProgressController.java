package com.studyplanner.progress;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.auth.AppUserPrincipal;

/**
 * GET /api/v1/progress/weekly?date=YYYY-MM-DD: progress of the Monday-to-Sunday week that contains the date
 * (the current week when no date is given). Read only; the owner always comes from the authenticated principal.
 * Success is {"data": ...}; errors are {"error": {"code", "message", "details"}} like the rest of the v1 API.
 */
@RestController
@RequestMapping("/api/v1/progress")
public class WeeklyProgressController {
    private static final Logger log = LoggerFactory.getLogger(WeeklyProgressController.class);

    private final WeeklyProgressService service;

    public WeeklyProgressController(WeeklyProgressService service) {
        this.service = service;
    }

    @GetMapping("/weekly")
    public ResponseEntity<?> weekly(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam(required = false) String date) {
        if (principal == null) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bạn cần đăng nhập.", List.of());
        }
        LocalDate day = null;
        if (date != null && !date.isBlank()) {
            try {
                day = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                return invalidDate("invalid_date", "Ngày phải có dạng YYYY-MM-DD.");
            }
        }
        try {
            return ResponseEntity.ok(Map.of("data", service.getWeek(principal.id(), day)));
        } catch (WeeklyProgressService.DateOutOfRangeException e) {
            return invalidDate("out_of_range", "Ngày phải nằm trong khoảng từ năm 2000 đến năm 2100.");
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex) {
        // Only the class name is logged: never request data or study content.
        log.error("Unexpected weekly progress error: {}", ex.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Đã xảy ra lỗi máy chủ.", List.of());
    }

    private static ResponseEntity<?> invalidDate(String issue, String message) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ.",
                List.of(Map.of("field", "date", "issue", issue, "message", message)));
    }

    private static ResponseEntity<?> error(HttpStatus status, String code, String message, List<Map<String, String>> details) {
        return ResponseEntity.status(status).body(Map.of("error", Map.of(
                "code", code, "message", message, "details", details)));
    }
}
