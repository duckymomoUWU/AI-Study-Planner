package com.studyplanner.goals;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.auth.AppUserPrincipal;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;
    private final PlanDraftRepository planDraftRepository;
    private final GeminiPlanService geminiPlanService;
    private final ObjectMapper objectMapper;

    public GoalController(
            GoalService goalService,
            PlanDraftRepository planDraftRepository,
            GeminiPlanService geminiPlanService,
            ObjectMapper objectMapper
    ) {
        this.goalService = Objects.requireNonNull(goalService, "goalService must not be null");
        this.planDraftRepository = Objects.requireNonNull(planDraftRepository, "planDraftRepository");
        this.geminiPlanService = Objects.requireNonNull(geminiPlanService, "geminiPlanService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public record GoalResponse(UUID id, String subject, String topics, LocalDate startDate,
                              LocalDate examDate, int minutesPerDay, String timezone, int version) {
        public static GoalResponse from(GoalJdbcRepository.StoredGoal stored) {
            return new GoalResponse(
                    stored.id(),
                    stored.subject(),
                    stored.topics(),
                    stored.startDate(),
                    stored.examDate(),
                    stored.minutesPerDay(),
                    stored.timezone(),
                    stored.version()
            );
        }
    }

    public record CreateGoalRequest(String subject, String topics, LocalDate startDate,
                                   LocalDate examDate, Integer minutesPerDay) {}

    public record UpdateGoalRequest(String subject, String topics, LocalDate startDate,
                                   LocalDate examDate, Integer minutesPerDay, Integer version) {}

    public record ConfirmDraftTaskRequest(String title, LocalDate scheduledDate, Integer estimatedMinutes, String source) {}

    public record ConfirmDraftRequest(Integer expectedGoalVersion, List<ConfirmDraftTaskRequest> tasks) {}

    @GetMapping
    public ResponseEntity<?> listGoals(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<GoalResponse> list = goalService.listGoals(principal.id()).stream()
                .map(GoalResponse::from)
                .toList();
        return ResponseEntity.ok(Map.of("items", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @PathVariable("id") UUID id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var goal = goalService.getGoal(principal.id(), id);
        return ResponseEntity.ok(GoalResponse.from(goal));
    }

    @PostMapping
    public ResponseEntity<?> createGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @RequestBody CreateGoalRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GoalPolicy.GoalInput input = new GoalPolicy.GoalInput(
                request.subject(),
                request.topics(),
                request.startDate(),
                request.examDate(),
                request.minutesPerDay()
        );
        var created = goalService.createGoal(principal.id(), input);
        return ResponseEntity.status(HttpStatus.CREATED).body(GoalResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable("id") UUID id,
                                       @RequestBody UpdateGoalRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GoalPolicy.GoalInput input = new GoalPolicy.GoalInput(
                request.subject(),
                request.topics(),
                request.startDate(),
                request.examDate(),
                request.minutesPerDay()
        );
        int version = request.version() == null ? 0 : request.version();
        var updated = goalService.updateGoal(principal.id(), id, input, version);
        return ResponseEntity.ok(GoalResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable("id") UUID id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        goalService.deleteGoal(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // US-06 AI PLAN DRAFTS (GEMINI FLASH)
    // =========================================================================

    @PostMapping("/{id}/plan-drafts")
    public ResponseEntity<?> createPlanDraft(@AuthenticationPrincipal AppUserPrincipal principal,
                                            @PathVariable("id") UUID goalId) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 1. Fetch goal snapshot
        var goal = goalService.getGoal(principal.id(), goalId);

        // 2. Call Gemini Flash (or fallback heuristic)
        var planReq = new GeminiPlanService.GeneratePlanRequest(
                goal.subject(),
                goal.topics(),
                goal.startDate(),
                goal.examDate(),
                goal.minutesPerDay(),
                goal.timezone()
        );
        String proposalJson = geminiPlanService.generateProposalJson(planReq);

        // 3. Store draft in DB
        Instant expiresAt = Instant.now().plus(Duration.ofHours(24));
        var draft = planDraftRepository.insertDraft(goal.id(), goal.version(), proposalJson, expiresAt);

        // 4. Return response
        try {
            JsonNode proposalNode = objectMapper.readTree(proposalJson);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", Map.of(
                            "id", draft.id(),
                            "goalId", draft.goalId(),
                            "goalVersion", draft.goalVersion(),
                            "proposal", proposalNode,
                            "createdAt", draft.createdAt().toString(),
                            "expiresAt", draft.expiresAt().toString()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", Map.of(
                            "id", draft.id(),
                            "goalId", draft.goalId(),
                            "goalVersion", draft.goalVersion(),
                            "proposal", proposalJson,
                            "createdAt", draft.createdAt().toString(),
                            "expiresAt", draft.expiresAt().toString()
                    )
            ));
        }
    }

    @GetMapping("/{id}/plan-drafts/latest")
    public ResponseEntity<?> getLatestPlanDraft(@AuthenticationPrincipal AppUserPrincipal principal,
                                               @PathVariable("id") UUID goalId) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Verify ownership
        goalService.getGoal(principal.id(), goalId);

        var draftOpt = planDraftRepository.findLatestDraft(principal.id(), goalId);
        if (draftOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "draft_not_found",
                    "message", "Chưa có bản nháp nào cho mục tiêu này."
            ));
        }

        var draft = draftOpt.get();
        try {
            JsonNode proposalNode = objectMapper.readTree(draft.proposalJson());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(
                            "id", draft.id(),
                            "goalId", draft.goalId(),
                            "goalVersion", draft.goalVersion(),
                            "proposal", proposalNode,
                            "createdAt", draft.createdAt().toString(),
                            "expiresAt", draft.expiresAt().toString()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(
                            "id", draft.id(),
                            "goalId", draft.goalId(),
                            "goalVersion", draft.goalVersion(),
                            "proposal", draft.proposalJson(),
                            "createdAt", draft.createdAt().toString(),
                            "expiresAt", draft.expiresAt().toString()
                    )
            ));
        }
    }

    @GetMapping("/{id}/plan-drafts/{draftId}")
    public ResponseEntity<?> getPlanDraft(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @PathVariable("id") UUID goalId,
                                         @PathVariable("draftId") UUID draftId) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Verify ownership
        goalService.getGoal(principal.id(), goalId);

        var draftOpt = planDraftRepository.findDraft(principal.id(), goalId, draftId);
        if (draftOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "draft_not_found",
                    "message", "Bản nháp không tồn tại hoặc đã hết hạn."
            ));
        }

        var draft = draftOpt.get();
        try {
            JsonNode proposalNode = objectMapper.readTree(draft.proposalJson());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(
                            "id", draft.id(),
                            "goalId", draft.goalId(),
                            "goalVersion", draft.goalVersion(),
                            "proposal", proposalNode,
                            "createdAt", draft.createdAt().toString(),
                            "expiresAt", draft.expiresAt().toString()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(
                            "id", draft.id(),
                            "goalId", draft.goalId(),
                            "goalVersion", draft.goalVersion(),
                            "proposal", draft.proposalJson(),
                            "createdAt", draft.createdAt().toString(),
                            "expiresAt", draft.expiresAt().toString()
                    )
            ));
        }
    }

    @PostMapping("/{id}/plan-drafts/{draftId}/confirm")
    public ResponseEntity<?> confirmPlanDraft(@AuthenticationPrincipal AppUserPrincipal principal,
                                             @PathVariable("id") UUID goalId,
                                             @PathVariable("draftId") UUID draftId,
                                             @RequestBody ConfirmDraftRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Verify ownership
        goalService.getGoal(principal.id(), goalId);

        if (request == null || request.tasks() == null || request.tasks().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "code", "invalid_request",
                    "message", "Danh sách công việc xác nhận không được để trống."
            ));
        }

        List<PlanDraftRepository.TaskToCreate> tasksToCreate = request.tasks().stream()
                .map(t -> new PlanDraftRepository.TaskToCreate(
                        t.title() != null ? t.title().trim() : "Ôn tập",
                        t.scheduledDate() != null ? t.scheduledDate() : LocalDate.now(),
                        t.estimatedMinutes() != null ? t.estimatedMinutes() : 60,
                        t.source() != null ? t.source() : "ai"
                ))
                .toList();

        List<UUID> createdIds = planDraftRepository.confirmDraft(principal.id(), goalId, draftId, tasksToCreate);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of(
                        "draftId", draftId,
                        "goalId", goalId,
                        "createdCount", createdIds.size(),
                        "createdTaskIds", createdIds
                )
        ));
    }

    @ExceptionHandler(GoalService.GoalValidationException.class)
    public ResponseEntity<?> handleValidation(GoalService.GoalValidationException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "code", "validation_failed",
                "message", "Dữ liệu mục tiêu không hợp lệ.",
                "fieldErrors", e.violations()
        ));
    }

    @ExceptionHandler(GoalService.GoalNotFoundException.class)
    public ResponseEntity<?> handleNotFound(GoalService.GoalNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "goal_not_found",
                "message", "Mục tiêu không tồn tại hoặc đã bị xóa."
        ));
    }

    @ExceptionHandler(GoalService.GoalConflictException.class)
    public ResponseEntity<?> handleConflict(GoalService.GoalConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "stale_goal_version",
                "message", "Mục tiêu vừa được cập nhật ở nơi khác. Vui lòng tải lại trước khi tiếp tục."
        ));
    }
}
