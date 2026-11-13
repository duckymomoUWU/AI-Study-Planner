package com.studyplanner.tasks;

import java.util.List;

public class TaskValidationException extends RuntimeException {
    public record FieldViolation(String field, String issue, String message) {}

    private final List<FieldViolation> violations;

    public TaskValidationException(List<FieldViolation> violations) {
        super(violations.isEmpty() ? "Dữ liệu công việc không hợp lệ." : violations.get(0).message());
        this.violations = List.copyOf(violations);
    }

    public TaskValidationException(String field, String issue, String message) {
        this(List.of(new FieldViolation(field, issue, message)));
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
