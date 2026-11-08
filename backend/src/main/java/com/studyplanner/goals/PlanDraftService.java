package com.studyplanner.goals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.api.RequestValidationException;
import com.studyplanner.tasks.*;

@Service
public class PlanDraftService {
    private final GoalService goalService;
    private final GoalJdbcRepository goals;
    private final TaskJdbcRepository tasks;
    private final PlanDraftRepository drafts;
    private final GeminiPlanService generator;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PlanDraftService(GoalService goalService, GoalJdbcRepository goals, TaskJdbcRepository tasks,
            PlanDraftRepository drafts, GeminiPlanService generator, ObjectMapper mapper, Clock clock) {
        this.goalService = goalService; this.goals = goals; this.tasks = tasks; this.drafts = drafts;
        this.generator = generator; this.mapper = mapper; this.clock = clock;
    }

    public record DraftResponse(UUID id, UUID goalId, int goalVersion, JsonNode proposal,
                                Instant createdAt, Instant expiresAt) {}
    public record ConfirmResponse(UUID draftId, UUID goalId, int goalVersion,
                                  int createdCount, List<UUID> createdTaskIds) {}
    public record Confirmation(ConfirmResponse result, boolean replayed) {}

    // Deliberately no surrounding DB transaction: the remote AI call must not hold locks/connections.
    public DraftResponse generate(UUID owner, UUID goalId) {
        var goal = goalService.getGoal(owner, goalId);
        String raw = generator.generateProposalJson(new GeminiPlanService.GeneratePlanRequest(goal.subject(),
                goal.topics(), goal.startDate(), goal.examDate(), goal.minutesPerDay(), goal.timezone(),
                allocations(owner, goalId)));
        JsonNode proposal;
        List<TaskPolicy.TaskInput> input;
        try {
            proposal = mapper.readTree(raw);
            if (proposal == null || !proposal.isObject() || proposal.size() != 1 || !proposal.has("tasks"))
                throw new IllegalArgumentException();
            input = parseTasks(proposal.get("tasks"));
            validate(goal, owner, input);
        } catch (PlanException ex) { throw ex; }
        catch (Exception ex) { throw new PlanException(502, "AI_INVALID_RESPONSE", "AI proposed invalid tasks."); }
        var draft = drafts.insertDraft(goal.id(), goal.version(), proposal.toString(), clock.instant().plus(Duration.ofHours(24)));
        return response(draft);
    }

    @Transactional(readOnly = true)
    public DraftResponse get(UUID owner, UUID goalId, UUID draftId) {
        var draft = drafts.findDraft(owner, goalId, draftId).orElseThrow(GoalService.GoalNotFoundException::new);
        requireUsable(owner, draft);
        return response(draft);
    }

    @Transactional(readOnly = true)
    public DraftResponse latest(UUID owner, UUID goalId) {
        goals.findOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        var draft = drafts.findLatestDraft(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        requireUsable(owner, draft);
        return response(draft);
    }

    @Transactional
    public Confirmation confirm(UUID owner, UUID goalId, UUID draftId, UUID key, JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("body", "invalid_type");
        body.fieldNames().forEachRemaining(name -> {
            if (!name.equals("expectedGoalVersion") && !name.equals("tasks")) throw invalid(name, "unknown_field");
        });
        var version = body.get("expectedGoalVersion");
        if (version == null || version.isNull()) throw invalid("expectedGoalVersion", "required");
        if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() < 0)
            throw invalid("expectedGoalVersion", "invalid_type");
        var input = parseTasks(body.get("tasks"));
        // Serialize confirmation keys per user, then budget-dependent writes per goal.
        drafts.lockOwner(owner);
        var goal = goals.lockOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        var draft = drafts.findDraft(owner, goalId, draftId).orElseThrow(GoalService.GoalNotFoundException::new);
        String fingerprint = fingerprint(goalId, draftId, version.intValue(), input);
        var receipt = drafts.receipt(owner, key);
        if (receipt.isPresent()) {
            if (!receipt.get().fingerprint().equals(fingerprint))
                throw new PlanException(409, "IDEMPOTENCY_CONFLICT", "The key was already used with another payload.");
            try { return new Confirmation(mapper.readValue(receipt.get().responseJson(), ConfirmResponse.class), true); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
        }
        requireUsable(owner, draft);
        if (version.intValue() != draft.goalVersion() || draft.goalVersion() != goal.version())
            throw new GoalService.GoalConflictException("stale_goal_version");
        validate(goal, owner, input);
        var ids = new ArrayList<UUID>();
        for (var task : input) ids.add(tasks.insertOwned(owner, goalId, task, "ai").id());
        goals.bumpVersion(owner, goalId);
        var result = new ConfirmResponse(draftId, goalId, goal.version() + 1, ids.size(), List.copyOf(ids));
        try { drafts.saveReceipt(owner, key, draftId, fingerprint, mapper.writeValueAsString(result)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
        return new Confirmation(result, false);
    }

    private void requireUsable(UUID owner, PlanDraftRepository.StoredDraft draft) {
        if (drafts.confirmed(owner, draft.id()))
            throw new PlanException(409, "DRAFT_ALREADY_CONFIRMED", "This draft has already been confirmed.");
        if (!clock.instant().isBefore(draft.expiresAt()))
            throw new PlanException(410, "DRAFT_EXPIRED", "This draft has expired; generate another draft.");
    }

    private void validate(GoalJdbcRepository.StoredGoal goal, UUID owner, List<TaskPolicy.TaskInput> input) {
        var violations = new ArrayList<TaskValidationException.FieldViolation>();
        for (var task : input) {
            violations.addAll(TaskPolicy.validateInput(task));
            violations.addAll(TaskPolicy.validateWithinGoal(task.scheduledDate(), goal.startDate(), goal.examDate()));
        }
        if (!violations.isEmpty()) throw new TaskValidationException(violations);
        Map<LocalDate, Integer> used = new HashMap<>();
        for (var task : goals.listScheduledTasksOwned(owner, goal.id()))
            used.merge(task.scheduledDate(), task.estimatedMinutes(), Integer::sum);
        long available = goal.startDate().datesUntil(goal.examDate())
                .mapToLong(day -> Math.max(0, goal.minutesPerDay() - used.getOrDefault(day, 0))).sum();
        long required = input.stream().mapToLong(TaskPolicy.TaskInput::estimatedMinutes).sum();
        if (required > available) throw new PlanException(422, "INSUFFICIENT_TIME", "The plan exceeds the remaining budget.",
                Map.of("requiredMinutes", required, "availableMinutes", available, "shortfallMinutes", required - available));
        for (var task : input) {
            int current = used.getOrDefault(task.scheduledDate(), 0);
            TaskPolicy.checkDailyBudget(task.scheduledDate(), current, task.estimatedMinutes(), goal.minutesPerDay());
            used.put(task.scheduledDate(), current + task.estimatedMinutes());
        }
    }

    private List<TaskPolicy.TaskInput> parseTasks(JsonNode list) {
        if (list == null || !list.isArray() || list.isEmpty()) throw invalid("tasks", "required");
        var result = new ArrayList<TaskPolicy.TaskInput>();
        for (var node : list) {
            var parsed = TaskRequest.parse(node, true);
            result.add(new TaskPolicy.TaskInput(parsed.title() == null ? null : parsed.title().trim(),
                    parsed.scheduledDate(), parsed.estimatedMinutes()));
        }
        return List.copyOf(result);
    }

    private String allocations(UUID owner, UUID goalId) {
        try {
            return mapper.writeValueAsString(goals.listScheduledTasksOwned(owner, goalId).stream().map(task -> Map.of(
                    "title", task.title(), "scheduledDate", task.scheduledDate().toString(),
                    "estimatedMinutes", task.estimatedMinutes())).toList());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    private String fingerprint(UUID goalId, UUID draftId, int version, List<TaskPolicy.TaskInput> input) {
        try {
            byte[] canonical = mapper.writeValueAsBytes(List.of(goalId, draftId, version, input));
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private DraftResponse response(PlanDraftRepository.StoredDraft draft) {
        try { return new DraftResponse(draft.id(), draft.goalId(), draft.goalVersion(), mapper.readTree(draft.proposalJson()),
                draft.createdAt(), draft.expiresAt()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    private static RequestValidationException invalid(String field, String issue) {
        return new RequestValidationException(List.of(new ErrorDetail(field, issue)));
    }
}
