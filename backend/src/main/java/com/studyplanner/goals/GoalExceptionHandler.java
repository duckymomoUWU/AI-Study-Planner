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
                        "Dá»¯ liá»‡u má»¥c tiÃªu khÃ´ng há»£p lá»‡.",
                        fieldErrors
                ));
    }

    @ExceptionHandler(GoalService.GoalNotFoundException.class)
    public ResponseEntity<TaskErrorResponse> handleGoalNotFound(GoalService.GoalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(TaskErrorResponse.of(
                        "NOT_FOUND",
                        "KhÃ´ng tÃ¬m tháº¥y má»¥c tiÃªu hoáº·c báº¡n khÃ´ng cÃ³ quyá»n truy cáº­p."
                ));
    }

    @ExceptionHandler(GoalService.GoalConflictException.class)
    public ResponseEntity<TaskErrorResponse> handleGoalConflict(GoalService.GoalConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(TaskErrorResponse.of(
                        "GOAL_VERSION_CONFLICT",
                        "Má»¥c tiÃªu Ä‘Ã£ Ä‘Æ°á»£c cáº­p nháº­t bá»Ÿi má»™t phiÃªn lÃ m viá»‡c khÃ¡c."
                ));
    }

    public record FieldViolationItem(String field, String code, String message) {}
}
