package com.studyplanner.tasks;

import java.util.List;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.studyplanner.tasks.dto.TaskErrorResponse;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TaskExceptionHandler {

    @ExceptionHandler(DailyBudgetExceededException.class)
    public ResponseEntity<TaskErrorResponse> handleDailyBudgetExceeded(DailyBudgetExceededException ex) {
        var detail = Map.of(
                "scheduledDate", ex.getScheduledDate().toString(),
                "budgetMinutes", ex.getBudgetMinutes(),
                "scheduledMinutes", ex.getScheduledMinutes(),
                "excessMinutes", ex.getExcessMinutes()
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(TaskErrorResponse.of(
                        "DAILY_BUDGET_EXCEEDED",
                        ex.getMessage(),
                        List.of(detail)
                ));
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<TaskErrorResponse> handleTaskNotFound(TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(TaskErrorResponse.of(
                        "NOT_FOUND",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(TaskValidationException.class)
    public ResponseEntity<TaskErrorResponse> handleTaskValidation(TaskValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(TaskErrorResponse.of(
                        "VALIDATION_ERROR",
                        ex.getMessage(),
                        ex.violations()
                ));
    }
}
