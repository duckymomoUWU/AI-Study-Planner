package com.studyplanner.goals;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Strict parser for goal request bodies (docs/backend-api-rules.md section 2).
 * The body is read as raw JSON so every problem is reported per field: wrong type, bad date, decimal
 * minutes, a null on a required field, or a field the client may not send (id, userId, createdAt, version...).
 */
final class GoalRequest {
    private static final Set<String> EDITABLE = Set.of(
            "subject", "topics", "startDate", "examDate", "minutesPerDay", "timezone");

    private GoalRequest() {}

    record Create(GoalPolicy.GoalInput input, String timezone, List<GoalPolicy.Violation> errors) {}

    record Patch(GoalPatch patch, Integer expectedVersion, List<GoalPolicy.Violation> errors) {}

    static Create parseCreate(JsonNode body) {
        List<GoalPolicy.Violation> errors = new ArrayList<>();
        if (!isObject(body, errors)) {
            return new Create(new GoalPolicy.GoalInput(null, null, null, null, null), null, errors);
        }
        rejectUnknown(body, Set.of(), errors);
        Fields f = readFields(body, false, errors);
        return new Create(new GoalPolicy.GoalInput(f.subject, f.topics, f.startDate, f.examDate, f.minutes),
                f.timezone, errors);
    }

    static Patch parsePatch(JsonNode body) {
        List<GoalPolicy.Violation> errors = new ArrayList<>();
        if (!isObject(body, errors)) {
            return new Patch(new GoalPatch(null, null, null, null, null, null), null, errors);
        }
        rejectUnknown(body, Set.of("expectedVersion"), errors);
        Fields f = readFields(body, true, errors);

        Integer expectedVersion = null;
        JsonNode version = body.get("expectedVersion");
        if (version == null) {
            errors.add(new GoalPolicy.Violation("expectedVersion", "required",
                    "expectedVersion là bắt buộc khi sửa mục tiêu."));
        } else if (version.isIntegralNumber() && version.canConvertToInt() && version.intValue() >= 0) {
            expectedVersion = version.intValue();
        } else {
            errors.add(new GoalPolicy.Violation("expectedVersion", "invalid_type",
                    "expectedVersion phải là số nguyên không âm."));
        }

        boolean anyEditable = false;
        for (String name : EDITABLE) {
            anyEditable |= body.has(name);
        }
        if (!anyEditable) {
            errors.add(new GoalPolicy.Violation("body", "empty_patch", "Cần gửi ít nhất một trường cần sửa."));
        }
        return new Patch(new GoalPatch(f.subject, f.topics, f.startDate, f.examDate, f.minutes, f.timezone),
                expectedVersion, errors);
    }

    private static final class Fields {
        String subject;
        String topics;
        LocalDate startDate;
        LocalDate examDate;
        Integer minutes;
        String timezone;
    }

    private static Fields readFields(JsonNode body, boolean patch, List<GoalPolicy.Violation> errors) {
        Fields f = new Fields();
        f.subject = text(body, "subject", patch, errors);
        f.topics = text(body, "topics", patch, errors);
        f.startDate = date(body, "startDate", patch, errors);
        f.examDate = date(body, "examDate", patch, errors);
        f.timezone = text(body, "timezone", patch, errors);
        JsonNode minutes = body.get("minutesPerDay");
        if (minutes != null && !minutes.isNull()) {
            if (minutes.isIntegralNumber() && minutes.canConvertToInt()) {
                f.minutes = minutes.intValue();
            } else {
                errors.add(new GoalPolicy.Violation("minutesPerDay", "out_of_range",
                        "Số phút mỗi ngày phải là số nguyên từ 15 đến 480."));
            }
        } else if (patch && minutes != null) {
            errors.add(nullNotAllowed("minutesPerDay"));
        }
        return f;
    }

    private static boolean isObject(JsonNode body, List<GoalPolicy.Violation> errors) {
        if (body == null || !body.isObject()) {
            errors.add(new GoalPolicy.Violation("body", "invalid_type", "Nội dung yêu cầu phải là một đối tượng JSON."));
            return false;
        }
        return true;
    }

    private static void rejectUnknown(JsonNode body, Set<String> extraAllowed, List<GoalPolicy.Violation> errors) {
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!EDITABLE.contains(name) && !extraAllowed.contains(name)) {
                errors.add(new GoalPolicy.Violation(name, "unknown_field", "Trường này không được phép gửi."));
            }
        }
    }

    private static GoalPolicy.Violation nullNotAllowed(String field) {
        return new GoalPolicy.Violation(field, "required", "Trường này không được để null.");
    }

    private static String text(JsonNode body, String field, boolean patch, List<GoalPolicy.Violation> errors) {
        JsonNode n = body.get(field);
        if (n == null) {
            return null;
        }
        if (n.isNull()) {
            if (patch) {
                errors.add(nullNotAllowed(field));
            }
            return null;
        }
        if (!n.isTextual()) {
            errors.add(new GoalPolicy.Violation(field, "invalid_type", "Giá trị phải là chuỗi."));
            return null;
        }
        return n.asText();
    }

    private static LocalDate date(JsonNode body, String field, boolean patch, List<GoalPolicy.Violation> errors) {
        JsonNode n = body.get(field);
        if (n == null) {
            return null;
        }
        if (n.isNull()) {
            if (patch) {
                errors.add(nullNotAllowed(field));
            }
            return null;
        }
        if (n.isTextual() && n.asText().isBlank()) {
            // On create a blank date is "required" (the policy reports it); on patch it is a bad value.
            if (patch) {
                errors.add(new GoalPolicy.Violation(field, "invalid_date", "Ngày phải có dạng YYYY-MM-DD."));
            }
            return null;
        }
        if (n.isTextual()) {
            try {
                return LocalDate.parse(n.asText());
            } catch (DateTimeParseException ignored) {
                // reported below
            }
        }
        errors.add(new GoalPolicy.Violation(field, "invalid_date", "Ngày phải có dạng YYYY-MM-DD."));
        return null;
    }
}
