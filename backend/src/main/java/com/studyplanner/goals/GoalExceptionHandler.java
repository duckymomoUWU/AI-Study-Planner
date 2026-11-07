package com.studyplanner.goals;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.studyplanner.tasks.dto.TaskErrorResponse;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GoalExceptionHandler {

    @ExceptionHandler(GoalService.GoalValidationException.class)
    public ResponseEntity<TaskErrorResponse> handleGoalValidation(GoalService.GoalValidationException ex) {
        List<FieldViolationItem> fieldErrors = new ArrayList<>();
        for (GoalPolicy.Violation v : ex.violations()) {
            fieldErrors.add(new FieldViolationItem(v.field(), v.code(), v.message()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(TaskErrorResponse.of(
                        "validation_failed",
                        "Dữ liệu mục tiêu không hợp lệ.",
                        fieldErrors
                ));
    }

    @ExceptionHandler(GoalService.GoalNotFoundException.class)
    public ResponseEntity<TaskErrorResponse> handleGoalNotFound(GoalService.GoalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(TaskErrorResponse.of(
                        "NOT_FOUND",
                        "Không tìm thấy mục tiêu hoặc bạn không có quyền truy cập."
                ));
    }

    @ExceptionHandler(GoalService.GoalConflictException.class)
    public ResponseEntity<TaskErrorResponse> handleGoalConflict(GoalService.GoalConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(TaskErrorResponse.of(
                        "GOAL_VERSION_CONFLICT",
                        "Mục tiêu đã được cập nhật bởi một phiên làm việc khác."
                ));
    }

    public record FieldViolationItem(String field, String code, String message) {}
}
