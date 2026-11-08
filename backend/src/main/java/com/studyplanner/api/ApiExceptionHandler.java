package com.studyplanner.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.auth.DuplicateEmailException;
import com.studyplanner.goals.GoalPolicy;
import com.studyplanner.goals.GoalService;
import com.studyplanner.tasks.TaskService;

/**
 * The single place that turns exceptions into the error contract (docs/backend-api-rules.md).
 * Logs only the exception class: never request bodies, passwords or study content.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String MSG_VALIDATION = "Dữ liệu không hợp lệ.";
    private static final String MSG_UNAUTHORIZED = "Bạn cần đăng nhập.";
    private static final String MSG_NOT_FOUND = "Không tìm thấy tài nguyên.";

    private static ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String code, String message,
                                                            List<ErrorDetail> details) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, details));
    }

    @ExceptionHandler(GoalService.GoalValidationException.class)
    ResponseEntity<ApiErrorResponse> goalValidation(GoalService.GoalValidationException ex) {
        List<ErrorDetail> details = ex.violations().stream().map(ApiExceptionHandler::detail).toList();
        // Plain input errors win (400); 409 only when every problem comes from existing tasks.
        boolean tasksConflict = !details.isEmpty() && ex.violations().stream().allMatch(v ->
                v.code().equals("task_on_or_after_exam_date") || v.code().equals("daily_budget_exceeded")
                        || v.code().equals("invalid_existing_task"));
        if (tasksConflict) {
            return respond(HttpStatus.CONFLICT, "GOAL_UPDATE_CONFLICT",
                    "Thay đổi làm công việc hiện có vi phạm ngày thi hoặc ngân sách thời gian.", details);
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu mục tiêu không hợp lệ.", details);
    }

    private static ErrorDetail detail(GoalPolicy.Violation v) {
        return new ErrorDetail(v.field(), v.code(), v.message());
    }

    @ExceptionHandler(GoalService.GoalNotFoundException.class)
    ResponseEntity<ApiErrorResponse> goalNotFound() {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy mục tiêu.", null);
    }

    @ExceptionHandler(TaskService.TaskNotFoundException.class)
    ResponseEntity<ApiErrorResponse> taskNotFound() {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy công việc.", null);
    }

    @ExceptionHandler(GoalService.GoalConflictException.class)
    ResponseEntity<ApiErrorResponse> goalVersionConflict() {
        return respond(HttpStatus.CONFLICT, "GOAL_VERSION_CONFLICT",
                "Mục tiêu đã được thay đổi ở nơi khác. Hãy tải lại rồi thử lại.", null);
    }

    @ExceptionHandler(com.studyplanner.tasks.TaskValidationException.class)
    ResponseEntity<ApiErrorResponse> taskValidation(com.studyplanner.tasks.TaskValidationException ex) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MSG_VALIDATION,
                ex.violations().stream().map(v -> new ErrorDetail(v.field(), v.issue(), v.message())).toList());
    }

    @ExceptionHandler(com.studyplanner.tasks.DailyBudgetExceededException.class)
    ResponseEntity<ApiErrorResponse> taskBudget(com.studyplanner.tasks.DailyBudgetExceededException ex) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "DAILY_BUDGET_EXCEEDED", ex.getMessage(), List.of(
                new ErrorDetail("scheduledDate", "daily_budget_exceeded", ex.getScheduledDate().toString()),
                new ErrorDetail("budgetMinutes", "budget", Integer.toString(ex.getBudgetMinutes())),
                new ErrorDetail("scheduledMinutes", "total", Integer.toString(ex.getScheduledMinutes())),
                new ErrorDetail("excessMinutes", "shortfall", Integer.toString(ex.getExcessMinutes()))));
    }

    @ExceptionHandler(RequestValidationException.class)
    ResponseEntity<ApiErrorResponse> requestValidation(RequestValidationException ex) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MSG_VALIDATION, ex.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> beanValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(new ErrorDetail(fieldError.getField(), issueOf(fieldError.getCode()),
                    fieldError.getDefaultMessage()));
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MSG_VALIDATION, details);
    }

    private static String issueOf(String constraint) {
        if (constraint == null) {
            return "invalid";
        }
        return switch (constraint) {
            case "NotBlank", "NotNull", "NotEmpty" -> "required";
            case "Email" -> "invalid_email";
            case "Size" -> "invalid_length";
            default -> constraint.toLowerCase(Locale.ROOT);
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> unreadable() {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MSG_VALIDATION,
                List.of(new ErrorDetail("body", "malformed_json", "Nội dung yêu cầu không phải JSON hợp lệ.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> missingParam(MissingServletRequestParameterException ex) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MSG_VALIDATION,
                List.of(new ErrorDetail(ex.getParameterName(), "required")));
    }

    /** A malformed ID in the path is "not found"; a malformed query parameter is a validation error. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException ex) {
        if (ex.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", MSG_NOT_FOUND, null);
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MSG_VALIDATION,
                List.of(new ErrorDetail(ex.getName(), "invalid_type")));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiErrorResponse> duplicateEmail() {
        return respond(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email đã được sử dụng.", null);
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    ResponseEntity<ApiErrorResponse> invalidCredentials() {
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Email hoặc mật khẩu không chính xác.", null);
    }

    @ExceptionHandler({AuthenticationException.class, UnauthenticatedException.class})
    ResponseEntity<ApiErrorResponse> unauthorized() {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", MSG_UNAUTHORIZED, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> csrfInvalid() {
        return respond(HttpStatus.FORBIDDEN, "CSRF_INVALID",
                "Yêu cầu không hợp lệ hoặc phiên đã hết hạn.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> unknownRoute() {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", MSG_NOT_FOUND, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> methodNotAllowed() {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Phương thức HTTP không được hỗ trợ cho đường dẫn này.", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex) {
        log.error("Unexpected API error: {}", ex.getClass().getName());
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "Đã xảy ra lỗi máy chủ.", null);
    }
}
