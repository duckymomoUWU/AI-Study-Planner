package com.studyplanner.goals;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Raw request body. Fields stay loosely typed so the server can report per-field errors
 * (wrong type, bad date, decimal minutes) instead of a generic parse failure.
 * Unknown properties such as userId or timezone are ignored and never trusted.
 */
public record GoalRequest(JsonNode subject, JsonNode topics, JsonNode startDate,
                          JsonNode examDate, JsonNode minutesPerDay, JsonNode version) {

    record Parsed(GoalPolicy.GoalInput input, List<GoalPolicy.Violation> errors) {}

    Parsed toInput() {
        List<GoalPolicy.Violation> errors = new ArrayList<>();
        String subjectText = text("subject", subject, errors);
        String topicsText = text("topics", topics, errors);
        LocalDate start = date("startDate", startDate, errors);
        LocalDate exam = date("examDate", examDate, errors);
        Integer minutes = null;
        if (minutesPerDay != null && !minutesPerDay.isNull()) {
            if (minutesPerDay.isIntegralNumber() && minutesPerDay.canConvertToInt()) {
                minutes = minutesPerDay.intValue();
            } else {
                errors.add(new GoalPolicy.Violation("minutesPerDay", "out_of_range",
                        "Số phút mỗi ngày phải là số nguyên từ 15 đến 480."));
            }
        }
        return new Parsed(new GoalPolicy.GoalInput(subjectText, topicsText, start, exam, minutes), errors);
    }

    /** Returns the version, or null when missing or not a non-negative integer. */
    Integer versionValue() {
        if (version != null && version.isIntegralNumber() && version.canConvertToInt()
                && version.intValue() >= 0) {
            return version.intValue();
        }
        return null;
    }

    private static String text(String field, JsonNode n, List<GoalPolicy.Violation> errors) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (!n.isTextual()) {
            errors.add(new GoalPolicy.Violation(field, "invalid_type", "Giá trị phải là chuỗi."));
            return null;
        }
        return n.asText();
    }

    private static LocalDate date(String field, JsonNode n, List<GoalPolicy.Violation> errors) {
        if (n == null || n.isNull()) {
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
