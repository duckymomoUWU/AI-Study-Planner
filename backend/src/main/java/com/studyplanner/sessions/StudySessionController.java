package com.studyplanner.sessions;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.api.ApiResponse;
import com.studyplanner.api.Paging;
import com.studyplanner.api.UnauthenticatedException;
import com.studyplanner.auth.AppUserPrincipal;

/** Study-session endpoints under /api/v1. The owner always comes from the authenticated principal. */
@RestController
@RequestMapping("/api/v1")
public class StudySessionController {
    private final StudySessionService service;

    public StudySessionController(StudySessionService service) {
        this.service = service;
    }

    /** The running or paused session, or {"session": null}. */
    @GetMapping("/study-sessions/active")
    public ApiResponse<StudySessionService.ActiveSessionView> active(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ApiResponse.of(service.active(owner(principal)));
    }

    @GetMapping("/study-sessions/task-totals")
    public ApiResponse<List<StudySessionRepository.TaskStudyTime>> taskTotals(
            @AuthenticationPrincipal AppUserPrincipal principal, @RequestParam String taskIds) {
        UUID owner = owner(principal);
        String[] values = taskIds.split(",", -1);
        if (values.length > 100) throw new com.studyplanner.api.RequestValidationException(List.of(
                new com.studyplanner.api.ApiErrorResponse.ErrorDetail("taskIds", "out_of_range", "Tối đa 100 công việc mỗi lần.")));
        var ids = java.util.Arrays.stream(values).map(value -> StudySessionRequest.uuid(value, "taskIds")).toList();
        return ApiResponse.of(service.taskStudyTimes(owner, ids));
    }

    /** Body: {"taskId"}. 201 for a new session, 200 when the same Idempotency-Key replays it. */
    @PostMapping("/study-sessions")
    public ResponseEntity<ApiResponse<StudySessionService.SessionView>> start(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode body) {
        UUID owner = owner(principal);
        UUID requestKey = StudySessionRequest.key(key);
        var result = service.start(owner, requestKey, StudySessionRequest.parseStart(body));
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(ApiResponse.of(result.session()));
    }

    @GetMapping("/study-sessions/{sessionId}")
    public ApiResponse<StudySessionService.SessionView> get(@AuthenticationPrincipal AppUserPrincipal principal,
                                                            @PathVariable UUID sessionId) {
        return ApiResponse.of(service.get(owner(principal), sessionId));
    }

    /** Body: {"action": PAUSE|RESUME|FINISH|CANCEL, "expectedVersion"}. */
    @PostMapping("/study-sessions/{sessionId}/actions")
    public ApiResponse<StudySessionService.SessionView> action(
            @AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode body) {
        UUID owner = owner(principal);
        UUID requestKey = StudySessionRequest.key(key);
        return ApiResponse.of(service.action(owner, sessionId, requestKey, StudySessionRequest.parseAction(body)).session());
    }

    @GetMapping("/study-sessions")
    public ApiResponse<List<StudySessionService.SessionView>> history(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        UUID owner = owner(principal);
        UUID task = taskId == null || taskId.isBlank() ? null : StudySessionRequest.uuid(taskId, "taskId");
        Paging paging = Paging.of(page, limit);
        var result = service.list(owner, task, paging.page(), paging.limit());
        return ApiResponse.page(result.items(), paging.page(), paging.limit(), result.totalItems());
    }

    @GetMapping("/goals/{goalId}/study-time")
    public ApiResponse<StudySessionRepository.StudyTime> studyTime(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                    @PathVariable UUID goalId) {
        return ApiResponse.of(service.studyTime(owner(principal), goalId));
    }

    private static UUID owner(AppUserPrincipal principal) {
        if (principal == null) throw new UnauthenticatedException();
        return principal.id();
    }
}
