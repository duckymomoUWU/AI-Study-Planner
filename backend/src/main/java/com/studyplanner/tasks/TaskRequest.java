package com.studyplanner.tasks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.api.RequestValidationException;

public final class TaskRequest {
    private TaskRequest() {}
    public static TaskService.TaskPatch parse(JsonNode body, boolean create) {
        List<ErrorDetail> errors = new ArrayList<>();
        if (body == null || !body.isObject()) throw new RequestValidationException(
                List.of(new ErrorDetail("body", "invalid_type")));
        Set<String> allowed = create ? Set.of("title", "scheduledDate", "estimatedMinutes")
                : Set.of("title", "scheduledDate", "estimatedMinutes", "completed");
        body.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) errors.add(new ErrorDetail(name, "unknown_field"));
        });
        if (!create && body.isEmpty()) errors.add(new ErrorDetail("body", "empty_patch"));
        String title = null;
        LocalDate date = null;
        Integer minutes = null;
        Boolean completed = null;
        for (String field : List.of("title", "scheduledDate", "estimatedMinutes")) {
            if ((create && !body.has(field)) || (body.has(field) && body.get(field).isNull()))
                errors.add(new ErrorDetail(field, "required"));
        }
        if (body.hasNonNull("title")) {
            if (!body.get("title").isTextual()) errors.add(new ErrorDetail("title", "invalid_type"));
            else title = body.get("title").textValue();
        }
        if (body.hasNonNull("scheduledDate")) {
            if (!body.get("scheduledDate").isTextual()) errors.add(new ErrorDetail("scheduledDate", "invalid_type"));
            else try { date = LocalDate.parse(body.get("scheduledDate").textValue()); }
            catch (java.time.DateTimeException ex) { errors.add(new ErrorDetail("scheduledDate", "invalid_date")); }
        }
        if (body.hasNonNull("estimatedMinutes")) {
            var node = body.get("estimatedMinutes");
            if (!node.isIntegralNumber() || !node.canConvertToInt()) errors.add(new ErrorDetail("estimatedMinutes", "invalid_type"));
            else minutes = node.intValue();
        }
        if (body.has("completed")) {
            if (body.get("completed").isNull()) errors.add(new ErrorDetail("completed", "required"));
            else if (!body.get("completed").isBoolean()) errors.add(new ErrorDetail("completed", "invalid_type"));
            else completed = body.get("completed").booleanValue();
        }
        if (!errors.isEmpty()) throw new RequestValidationException(errors);
        return new TaskService.TaskPatch(title, date, minutes, completed);
    }
}
