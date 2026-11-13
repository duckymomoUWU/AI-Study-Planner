package com.studyplanner.recall;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class RecallException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<FieldErrorItem> details;
    private final Map<String, Object> context;

    public record FieldErrorItem(String field, String issue) {}

    public RecallException(HttpStatus status, String code, String message) {
        this(status, code, message, Collections.emptyList(), Collections.emptyMap());
    }

    public RecallException(HttpStatus status, String code, String message, List<FieldErrorItem> details) {
        this(status, code, message, details, Collections.emptyMap());
    }

    public RecallException(HttpStatus status, String code, String message, List<FieldErrorItem> details, Map<String, Object> context) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details != null ? details : Collections.emptyList();
        this.context = context != null ? context : Collections.emptyMap();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldErrorItem> getDetails() {
        return details;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public static RecallException notFound(String message) {
        return new RecallException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static RecallException validation(String message, List<FieldErrorItem> details) {
        return new RecallException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, details);
    }

    public static RecallException validation(String field, String issue, String message) {
        return new RecallException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                List.of(new FieldErrorItem(field, issue)));
    }

    public static RecallException deckVersionConflict(int currentVersion) {
        return new RecallException(HttpStatus.CONFLICT, "DECK_VERSION_CONFLICT",
                "Bộ thẻ đã được cập nhật ở nơi khác. Vui lòng tải lại trước khi tiếp tục.",
                List.of(new FieldErrorItem("expectedVersion", "stale_version")),
                Map.of("currentVersion", currentVersion));
    }

    public static RecallException cardVersionConflict(int currentVersion) {
        return new RecallException(HttpStatus.CONFLICT, "CARD_VERSION_CONFLICT",
                "Thẻ học đã được cập nhật ở nơi khác. Vui lòng tải lại trước khi tiếp tục.",
                List.of(new FieldErrorItem("expectedVersion", "stale_version")),
                Map.of("currentVersion", currentVersion));
    }

    public static RecallException deckStateConflict(String message) {
        return new RecallException(HttpStatus.CONFLICT, "DECK_STATE_CONFLICT", message);
    }

    public static RecallException idempotencyConflict(String message) {
        return new RecallException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", message);
    }

    public static RecallException generationInProgress() {
        return new RecallException(HttpStatus.CONFLICT, "GENERATION_IN_PROGRESS",
                "Bộ thẻ đang được tạo cho yêu cầu này. Hãy chờ vài giây rồi thử lại.");
    }

    public static RecallException sourceTooLong(String message) {
        return new RecallException(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_TOO_LONG", message);
    }

    public static RecallException sourceNoText(String message) {
        return new RecallException(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_NO_TEXT", message);
    }

    public static RecallException insufficientMaterial(String message) {
        return new RecallException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_MATERIAL", message);
    }

    public static RecallException noSuitableContent(String message) {
        return new RecallException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_SUITABLE_CONTENT", message);
    }

    public static RecallException allCardsFailedQuality(String message) {
        return new RecallException(HttpStatus.UNPROCESSABLE_ENTITY, "ALL_CARDS_FAILED_QUALITY", message);
    }

    public static RecallException aiMalformedResponse(String message) {
        return new RecallException(HttpStatus.BAD_GATEWAY, "AI_MALFORMED_RESPONSE", message);
    }

    public static RecallException aiInvalidResponse(String message) {
        return new RecallException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", message);
    }

    public static RecallException aiUnavailable(String message) {
        return new RecallException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", message);
    }

    public static RecallException aiTimeout(String message) {
        return new RecallException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", message);
    }
}
