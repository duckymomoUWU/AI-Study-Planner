package com.studyplanner.sessions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.studyplanner.api.ApiErrorResponse.ErrorDetail;

/** A session error with its HTTP status and API code; ApiExceptionHandler turns it into the error envelope. */
public class StudySessionException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<ErrorDetail> details;
    private final Map<String, Object> context;

    public StudySessionException(int status, String code, String message) {
        this(status, code, message, List.of(), Map.of());
    }

    public StudySessionException(int status, String code, String message, List<ErrorDetail> details,
                                 Map<String, Object> context) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
        this.context = Map.copyOf(context);
    }

    public int status() { return status; }
    public String code() { return code; }
    public List<ErrorDetail> details() { return details; }
    public Map<String, Object> context() { return context; }

    public static StudySessionException notFound() {
        return new StudySessionException(404, "NOT_FOUND", "Không tìm thấy phiên học.");
    }

    public static StudySessionException taskNotFound() {
        return new StudySessionException(404, "NOT_FOUND", "Không tìm thấy công việc.");
    }

    public static StudySessionException goalNotFound() {
        return new StudySessionException(404, "NOT_FOUND", "Không tìm thấy mục tiêu.");
    }

    public static StudySessionException alreadyActive(UUID activeSessionId) {
        return new StudySessionException(409, "SESSION_ALREADY_ACTIVE",
                "Bạn đang có một phiên học chưa kết thúc.", List.of(),
                activeSessionId == null ? Map.of() : Map.of("activeSessionId", activeSessionId.toString()));
    }

    public static StudySessionException versionConflict(int currentVersion) {
        return new StudySessionException(409, "SESSION_VERSION_CONFLICT",
                "Phiên học đã thay đổi ở nơi khác. Hãy tải lại trước khi tiếp tục.",
                List.of(new ErrorDetail("expectedVersion", "stale_version")), Map.of("currentVersion", currentVersion));
    }

    public static StudySessionException stateConflict(String status, String action) {
        return new StudySessionException(409, "SESSION_STATE_CONFLICT",
                "Không thể " + action + " khi phiên học đang ở trạng thái " + status + ".", List.of(),
                Map.of("status", status));
    }

    public static StudySessionException finishRequired() {
        return new StudySessionException(409, "FOCUS_FINISH_REQUIRED",
                "Đã đủ thời gian tập trung. Hãy kết thúc phiên học.");
    }

    public static StudySessionException idempotencyConflict() {
        return new StudySessionException(409, "IDEMPOTENCY_CONFLICT",
                "Idempotency-Key này đã được dùng cho một yêu cầu khác.");
    }
}
