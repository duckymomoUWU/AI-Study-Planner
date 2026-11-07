package com.studyplanner.goals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps goal errors to the JSON contract. Logs never include request bodies or study content. */
@RestControllerAdvice(basePackageClasses = GoalController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GoalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GoalExceptionHandler.class);

    @ExceptionHandler(GoalService.GoalValidationException.class)
    ResponseEntity<GoalErrorResponse> validation(GoalService.GoalValidationException ex) {
        return ResponseEntity.badRequest().body(new GoalErrorResponse(
                "validation_failed", "Dữ liệu mục tiêu không hợp lệ.", ex.violations()));
    }

    @ExceptionHandler(GoalService.GoalNotFoundException.class)
    ResponseEntity<GoalErrorResponse> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new GoalErrorResponse("goal_not_found", "Không tìm thấy mục tiêu."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<GoalErrorResponse> badId() {
        // A malformed UUID gets the same response as an unknown goal.
        return notFound();
    }

    @ExceptionHandler(GoalController.UnauthenticatedException.class)
    ResponseEntity<GoalErrorResponse> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new GoalErrorResponse("unauthenticated", "Bạn cần đăng nhập."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<GoalErrorResponse> unreadable() {
        return ResponseEntity.badRequest().body(new GoalErrorResponse(
                "validation_failed", "Nội dung yêu cầu không phải JSON hợp lệ."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<GoalErrorResponse> unexpected(Exception ex) {
        log.error("Unexpected goal API error: {}", ex.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GoalErrorResponse("internal_error", "Đã xảy ra lỗi máy chủ."));
    }
}
