package com.studyplanner.tasks;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.api.ApiResponse;
import com.studyplanner.api.Paging;
import com.studyplanner.api.RequestValidationException;
import com.studyplanner.api.UnauthenticatedException;
import com.studyplanner.auth.AppUserPrincipal;

/**
 * Task endpoints under /api/v1 (docs/backend-api-rules.md): read tasks and mark them done or re-open them.
 * Creating, editing and deleting tasks belongs to ASP-10. The owner always comes from the principal.
 */
@RestController
@RequestMapping("/api/v1")
public class TaskController {
    /** Spec fields of PATCH that ASP-10 will add; refused for now with a clearer issue than unknown_field. */
    private static final Set<String> LATER_FIELDS = Set.of("title", "scheduledDate", "estimatedMinutes");

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /** Tasks of one day across all of the caller's goals. {@code date} is required. */
    @GetMapping("/tasks")
    public ApiResponse<List<TaskResponse>> listByDate(@AuthenticationPrincipal AppUserPrincipal principal,
                                                      @RequestParam(required = false) String date,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer limit) {
        UUID owner = owner(principal);
        LocalDate day = parseDate(date, true);
        Paging paging = Paging.of(page, limit);
        TaskService.TaskPage result = service.listByDate(owner, day, paging.page(), paging.limit());
        return ApiResponse.page(result.items().stream().map(TaskResponse::from).toList(),
                paging.page(), paging.limit(), result.totalItems());
    }

    @GetMapping("/goals/{goalId}/tasks")
    public ApiResponse<List<TaskResponse>> listByGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                                      @PathVariable UUID goalId,
                                                      @RequestParam(required = false) String date,
                                                      @RequestParam(required = false) Boolean completed,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer limit) {
        UUID owner = owner(principal);
        LocalDate day = parseDate(date, false);
        Paging paging = Paging.of(page, limit);
        TaskService.TaskPage result = service.listByGoal(owner, goalId, day, completed, paging.page(), paging.limit());
        return ApiResponse.page(result.items().stream().map(TaskResponse::from).toList(),
                paging.page(), paging.limit(), result.totalItems());
    }

    @GetMapping("/goals/{goalId}/tasks/{taskId}")
    public ApiResponse<TaskResponse> get(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @PathVariable UUID goalId, @PathVariable UUID taskId) {
        return ApiResponse.of(TaskResponse.from(service.getTask(owner(principal), goalId, taskId)));
    }

    /** Body: {"completed": true|false}, the target state (never a toggle). */
    @PatchMapping("/goals/{goalId}/tasks/{taskId}")
    public ApiResponse<TaskResponse> patch(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @PathVariable UUID goalId, @PathVariable UUID taskId,
                                           @RequestBody JsonNode body) {
        UUID owner = owner(principal);
        boolean completed = parseCompleted(body);
        return ApiResponse.of(TaskResponse.from(service.setCompleted(owner, goalId, taskId, completed)));
    }

    private static boolean parseCompleted(JsonNode body) {
        List<ErrorDetail> errors = new ArrayList<>();
        if (body == null || !body.isObject()) {
            throw new RequestValidationException(List.of(new ErrorDetail("body", "invalid_type",
                    "Nội dung yêu cầu phải là một đối tượng JSON.")));
        }
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.equals("completed")) {
                continue;
            }
            errors.add(LATER_FIELDS.contains(name)
                    ? new ErrorDetail(name, "not_supported", "Sửa nội dung công việc chưa được hỗ trợ.")
                    : new ErrorDetail(name, "unknown_field", "Trường này không được phép gửi."));
        }
        JsonNode completed = body.get("completed");
        if (completed == null || completed.isNull()) {
            errors.add(new ErrorDetail("completed", "required", "completed là bắt buộc (true hoặc false)."));
        } else if (!completed.isBoolean()) {
            errors.add(new ErrorDetail("completed", "invalid_type", "completed phải là true hoặc false."));
        }
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
        return completed.booleanValue();
    }

    private static LocalDate parseDate(String date, boolean required) {
        if (date == null || date.isBlank()) {
            if (required) {
                throw new RequestValidationException(List.of(new ErrorDetail("date", "required",
                        "date là bắt buộc, dạng YYYY-MM-DD.")));
            }
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new RequestValidationException(List.of(new ErrorDetail("date", "invalid_date",
                    "Ngày phải có dạng YYYY-MM-DD.")));
        }
    }

    private static UUID owner(AppUserPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        return principal.id();
    }
}
