package com.studyplanner.tasks;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
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
 * Includes manual create, edit and delete (US2/sprint3). The owner always comes from the principal.
 */
@RestController
@RequestMapping("/api/v1")
public class TaskController {

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
        var parsed = TaskRequest.parse(body, false);
        if (body.size() == 1 && parsed.completed() != null) {
            return ApiResponse.of(TaskResponse.from(service.setCompleted(owner, goalId, taskId, parsed.completed())));
        }
        return ApiResponse.of(TaskResponse.from(service.updateTask(owner, goalId, taskId, parsed)));
    }

    @PostMapping("/goals/{goalId}/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> create(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId, @RequestBody JsonNode body) {
        UUID userId = owner(principal);
        var parsed = TaskRequest.parse(body, true);
        var input = new TaskPolicy.TaskInput(parsed.title(), parsed.scheduledDate(), parsed.estimatedMinutes());
        return ResponseEntity.status(201).body(ApiResponse.of(TaskResponse.from(service.createTask(userId, goalId, input))));
    }

    @DeleteMapping("/goals/{goalId}/tasks/{taskId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId, @PathVariable UUID taskId) {
        service.deleteTask(owner(principal), goalId, taskId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/tasks/{taskId}")
    public ApiResponse<TaskResponse> patchDirect(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID taskId, @RequestBody JsonNode body) {
        UUID userId = owner(principal);
        return ApiResponse.of(TaskResponse.from(service.updateTaskDirect(userId, taskId, TaskRequest.parse(body, false))));
    }

    @GetMapping("/tasks/overdue")
    public ApiResponse<List<TaskResponse>> overdue(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ApiResponse.of(service.listOverdue(owner(principal)).stream().map(TaskResponse::from).toList());
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
