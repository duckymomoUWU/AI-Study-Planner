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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.api.UnauthenticatedException;
import com.studyplanner.auth.AppUserPrincipal;

/**
 * AI features of a goal under /api/v1/goals (dev1, sprint 3): syllabus autofill and plan drafts (US-06). Moved out
 * of GoalController so the strict goal CRUD stays small; paths are unchanged apart from the /api/v1 prefix. Errors use
 * the shared envelope {"error": {"code", "message", "details"}}; an unknown or foreign goal is the shared 404.
 */
@RestController
@RequestMapping("/api/v1/goals")
public class PlanDraftController {
    private final GoalService goalService;
    private final PlanDraftRepository planDraftRepository;
    private final GeminiPlanService geminiPlanService;
    private final ObjectMapper objectMapper;

    public PlanDraftController(GoalService goalService, PlanDraftRepository planDraftRepository,
                               GeminiPlanService geminiPlanService, ObjectMapper objectMapper) {
        this.goalService = Objects.requireNonNull(goalService, "goalService must not be null");
        this.planDraftRepository = Objects.requireNonNull(planDraftRepository, "planDraftRepository");
        this.geminiPlanService = Objects.requireNonNull(geminiPlanService, "geminiPlanService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public record ConfirmDraftTaskRequest(String title, LocalDate scheduledDate, Integer estimatedMinutes, String source) {}

    public record ConfirmDraftRequest(Integer expectedGoalVersion, List<ConfirmDraftTaskRequest> tasks) {}

    public record ParseSyllabusRequest(String content, String base64, String mimeType, String fileName) {}

    private static UUID owner(AppUserPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        return principal.id();
    }

    private static ResponseEntity<?> error(HttpStatus status, String code, String message, List<Map<String, String>> details) {
        return ResponseEntity.status(status).body(Map.of("error", Map.of(
                "code", code, "message", message, "details", details)));
    }

    // =========================================================================
    // SYLLABUS DOCUMENT PARSING (GEMINI 3.8 FLASH)
    // =========================================================================

    @PostMapping("/parse-syllabus")
    public ResponseEntity<?> parseSyllabus(@RequestBody(required = false) ParseSyllabusRequest request) {
        String content = request != null ? request.content() : "";
        String base64 = request != null ? request.base64() : null;
        String mimeType = request != null ? request.mimeType() : "text/plain";
        String fileName = request != null ? request.fileName() : "de_cuong.txt";

        GeminiPlanService.SyllabusData result = geminiPlanService.parseSyllabus(content, base64, mimeType, fileName);
        return ResponseEntity.ok(Map.of(
                "data", Map.of(
                        "subject", result.subject(),
                        "topics", result.topics(),
                        "startDate", result.startDate().toString(),
                        "examDate", result.examDate().toString(),
                        "minutesPerDay", result.minutesPerDay()
                )
        ));
    }

    // =========================================================================
    // US-06 AI PLAN DRAFTS (GEMINI FLASH)
    // =========================================================================

    @PostMapping("/{id}/plan-drafts")
    public ResponseEntity<?> createPlanDraft(@AuthenticationPrincipal AppUserPrincipal principal,
                                            @PathVariable("id") UUID goalId) {
        owner(principal);

        // 1. Fetch goal snapshot
        var goal = goalService.getGoal(owner(principal), goalId);

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
        owner(principal);
        // Verify ownership
        goalService.getGoal(owner(principal), goalId);

        var draftOpt = planDraftRepository.findLatestDraft(owner(principal), goalId);
        if (draftOpt.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Chưa có bản nháp nào cho mục tiêu này.", List.of());
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

    @GetMapping("/plan-drafts/{draftId}")
    public ResponseEntity<?> getPlanDraftById(@AuthenticationPrincipal AppUserPrincipal principal,
                                             @PathVariable("draftId") UUID draftId) {
        owner(principal);

        var draftOpt = planDraftRepository.findDraftById(owner(principal), draftId);
        if (draftOpt.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Bản nháp kế hoạch không tồn tại hoặc đã hết hạn.", List.of());
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
        owner(principal);
        // Verify ownership
        goalService.getGoal(owner(principal), goalId);

        if (request == null || request.tasks() == null || request.tasks().isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Danh sách công việc xác nhận không được để trống.",
                    List.of(Map.of("field", "tasks", "issue", "required", "message", "Cần ít nhất một công việc.")));
        }

        List<PlanDraftRepository.TaskToCreate> tasksToCreate = request.tasks().stream()
                .map(t -> new PlanDraftRepository.TaskToCreate(
                        t.title() != null ? t.title().trim() : "Ôn tập",
                        t.scheduledDate() != null ? t.scheduledDate() : LocalDate.now(),
                        t.estimatedMinutes() != null ? t.estimatedMinutes() : 60,
                        t.source() != null ? t.source() : "ai"
                ))
                .toList();

        List<UUID> createdIds = planDraftRepository.confirmDraft(owner(principal), goalId, draftId, tasksToCreate);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of(
                        "draftId", draftId,
                        "goalId", goalId,
                        "createdCount", createdIds.size(),
                        "createdTaskIds", createdIds
                )
        ));
    }
}
