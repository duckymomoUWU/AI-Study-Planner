package com.studyplanner.sessions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.api.RequestValidationException;

/** Strict parsing of the session request bodies and headers: unknown fields, wrong types and nulls are 400. */
public final class StudySessionRequest {
    public record Start(UUID taskId) {}

    public record ActionRequest(StudySessionPolicy.Action action, int expectedVersion) {}

    private StudySessionRequest() {}

    public static UUID key(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RequestValidationException(List.of(new ErrorDetail("Idempotency-Key", "required",
                    "Thiếu header Idempotency-Key (UUID).")));
        }
        return uuid(raw, "Idempotency-Key");
    }

    public static UUID uuid(String raw, String field) {
        try {
            UUID parsed = UUID.fromString(raw);
            if (!parsed.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw new RequestValidationException(List.of(new ErrorDetail(field, "invalid_type",
                    field + " phải là UUID hợp lệ.")));
        }
    }

    public static Start parseStart(JsonNode body) {
        List<ErrorDetail> errors = new ArrayList<>();
        requireObject(body, Set.of("taskId"), errors);
        UUID taskId = null;
        if (errors.isEmpty()) {
            JsonNode node = body.get("taskId");
            if (node == null || node.isNull()) {
                errors.add(new ErrorDetail("taskId", "required"));
            } else if (!node.isTextual()) {
                errors.add(new ErrorDetail("taskId", "invalid_type"));
            } else {
                try {
                    taskId = UUID.fromString(node.textValue());
                } catch (IllegalArgumentException ex) {
                    errors.add(new ErrorDetail("taskId", "invalid_type"));
                }
            }
        }
        fail(errors);
        return new Start(taskId);
    }

    public static ActionRequest parseAction(JsonNode body) {
        List<ErrorDetail> errors = new ArrayList<>();
        requireObject(body, Set.of("action", "expectedVersion"), errors);
        StudySessionPolicy.Action action = null;
        int version = 0;
        if (errors.isEmpty()) {
            JsonNode a = body.get("action");
            if (a == null || a.isNull()) {
                errors.add(new ErrorDetail("action", "required"));
            } else if (!a.isTextual()) {
                errors.add(new ErrorDetail("action", "invalid_type"));
            } else {
                try {
                    action = StudySessionPolicy.Action.valueOf(a.textValue());
                } catch (IllegalArgumentException ex) {
                    errors.add(new ErrorDetail("action", "invalid_value"));
                }
            }
            JsonNode v = body.get("expectedVersion");
            if (v == null || v.isNull()) {
                errors.add(new ErrorDetail("expectedVersion", "required"));
            } else if (!v.isIntegralNumber() || !v.canConvertToInt()) {
                errors.add(new ErrorDetail("expectedVersion", "invalid_type"));
            } else if (v.intValue() < 0) {
                errors.add(new ErrorDetail("expectedVersion", "out_of_range"));
            } else {
                version = v.intValue();
            }
        }
        fail(errors);
        return new ActionRequest(action, version);
    }

    private static void requireObject(JsonNode body, Set<String> allowed, List<ErrorDetail> errors) {
        if (body == null || !body.isObject()) {
            errors.add(new ErrorDetail("body", "invalid_type"));
            return;
        }
        body.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) errors.add(new ErrorDetail(name, "unknown_field"));
        });
    }

    private static void fail(List<ErrorDetail> errors) {
        if (!errors.isEmpty()) throw new RequestValidationException(errors);
    }
}
