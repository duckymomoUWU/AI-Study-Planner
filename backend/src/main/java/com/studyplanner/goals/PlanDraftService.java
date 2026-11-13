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
    private final StudyBudgetService budgets;

    public PlanDraftService(GoalService goalService, GoalJdbcRepository goals, TaskJdbcRepository tasks,
            PlanDraftRepository drafts, GeminiPlanService generator, ObjectMapper mapper, Clock clock, StudyBudgetService budgets) {
        this.goalService = goalService; this.goals = goals; this.tasks = tasks; this.drafts = drafts;
        this.generator = generator; this.mapper = mapper; this.clock = clock;
        this.budgets = budgets;
    }

    public record DraftResponse(UUID id, UUID goalId, int goalVersion, JsonNode proposal,
                                Instant createdAt, Instant expiresAt, String status) {
        public DraftResponse(UUID id, UUID goalId, int goalVersion, JsonNode proposal, Instant createdAt, Instant expiresAt) {
            this(id,goalId,goalVersion,proposal,createdAt,expiresAt,"DRAFT");
        }
    }
    public record ConfirmResponse(UUID draftId, UUID goalId, int goalVersion,
                                  int createdCount, List<UUID> createdTaskIds, UUID remainingDraftId, int remainingCount) {
        public ConfirmResponse(UUID draftId, UUID goalId, int goalVersion, int createdCount, List<UUID> createdTaskIds) {
            this(draftId, goalId, goalVersion, createdCount, createdTaskIds, null, 0);
        }
    }
    public record Confirmation(ConfirmResponse result, boolean replayed) {}

    // Deliberately no surrounding DB transaction: the remote AI call must not hold locks/connections.
    public DraftResponse generate(UUID owner, UUID goalId) {
        var goal = goalService.getGoal(owner, goalId);
        budgets.requireConfigured(owner);
        if(GoalPlanning.configured(goal)) return generateScoped(owner,goalId,goal.planningScope());
        requireFutureStudyDay(goal);
        String raw = generator.generateProposalJson(new GeminiPlanService.GeneratePlanRequest(goal.subject(),
                goal.topics(), goal.startDate(), goal.examDate(), goal.minutesPerDay(), goal.timezone(),
                allocations(owner, goalId)));
        JsonNode proposal;
        List<TaskPolicy.TaskInput> input;
        try {
            proposal = mapper.readTree(raw);
            if (proposal == null || !proposal.isObject() || proposal.size() != 1 || !proposal.has("tasks"))
                throw new IllegalArgumentException();
            input = parseTasks(proposal.get("tasks"), true);
            validate(goal, owner, input);
            var preferenceScope=preferenceScope(owner,goal);
            ((com.fasterxml.jackson.databind.node.ObjectNode)proposal).set("preferenceReview",preferenceScope);
            ((com.fasterxml.jackson.databind.node.ObjectNode)proposal).putObject("summary")
                .set("preferenceExcessDays",PlanScheduling.preferenceExcess(mapper,proposal.path("tasks"),preferenceScope,goals.listScheduledTasksOwned(owner,goalId)));
        } catch (PlanException ex) { throw ex; }
        catch (Exception ex) { throw new PlanException(502, "AI_INVALID_RESPONSE", "AI proposed invalid tasks."); }
        var draft = drafts.insertDraft(goal.id(), goal.version(), proposal.toString(), clock.instant().plus(Duration.ofHours(24)));
        return response(draft);
    }

    public DraftResponse generateScoped(UUID owner, UUID goalId, JsonNode body) {
        var goal = goalService.savePlanningScope(owner,goalId,body);
        var today = LocalDate.now(clock.withZone(java.time.ZoneId.of(goal.timezone())));
        var scope = PlanScheduling.scope(mapper, goal.planningScope(), goal, today);
        budgets.constrain(owner,goalId,scope);
        var existing = goals.listScheduledTasksOwned(owner, goalId);
        if (PlanScheduling.remaining(scope, existing).values().stream().mapToInt(Integer::intValue).sum() == 0)
            throw new PlanException(422, "NO_STUDY_TIME", "Quỹ phút học còn lại bằng 0; hãy điều chỉnh thời gian trước khi gọi AI.");
        com.fasterxml.jackson.databind.node.ObjectNode proposal = null;
        String feedback = null;
        String savedAllocations = allocations(owner, goalId);
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String raw = generator.generateProposalJson(new GeminiPlanService.GeneratePlanRequest(goal.subject(), goal.topics(),
                    goal.startDate(), goal.examDate(), goal.minutesPerDay(), goal.timezone(), savedAllocations, scope.toString(),feedback));
                JsonNode input;
                try { input = mapper.readTree(raw); }
                catch (Exception ex) { throw new PlanException(502,"AI_INVALID_RESPONSE","AI trả JSON công việc chưa hợp lệ. Hãy thử lại; lịch đã lưu không thay đổi.",Map.of("reasonCode","INVALID_JSON")); }
                proposal = PlanScheduling.schedule(mapper,input,scope,existing);
                break;
            } catch (PlanException ex) {
                if (!ex.code().equals("AI_INVALID_RESPONSE") || attempt == 1) throw ex;
                feedback = String.valueOf(ex.context().getOrDefault("reasonCode","INVALID_FIELDS"));
            }
        }
        // A slow provider response must not create a usable draft for inputs changed in another request.
        if (goalService.getGoal(owner, goalId).version() != goal.version())
            throw new GoalService.GoalConflictException("stale_goal_version");
        return response(drafts.insertDraft(goalId, goal.version(), proposal.toString(), clock.instant().plus(Duration.ofHours(24))));
    }

    private void requireFutureStudyDay(GoalJdbcRepository.StoredGoal goal) {
        if (!goal.examDate().isAfter(LocalDate.now(clock.withZone(java.time.ZoneId.of(goal.timezone())))))
            throw new PlanException(422, "NO_STUDY_TIME", "Không còn ngày học hợp lệ trước ngày thi.");
    }

    @Transactional(readOnly = true)
    public List<DraftResponse> pending(UUID owner, UUID goalId) {
        goalService.getGoal(owner, goalId);
        return drafts.pending(owner, goalId).stream().map(draft -> readResponse(owner,goalId,draft)).toList();
    }

    // Reuse the reviewed work and estimates; changing the available time does not call AI again.
    @Transactional
    public DraftResponse reschedule(UUID owner, UUID goalId, UUID draftId) {
        return reschedule(owner,goalId,draftId,null);
    }

    @Transactional
    public DraftResponse reschedule(UUID owner, UUID goalId, UUID draftId, Boolean respectPreferences) {
        budgets.lock(owner);
        var goal = goals.lockOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        var draft = drafts.findDraft(owner, goalId, draftId).orElseThrow(GoalService.GoalNotFoundException::new);
        requireUsable(owner, draft);
        var proposal = response(draft).proposal();
        if (!proposal.has("scope")) throw invalid("draft", "scoped_draft_required");
        var today = LocalDate.now(clock.withZone(java.time.ZoneId.of(goal.timezone())));
        var scope = PlanScheduling.scope(mapper, goal.planningScope(), goal, today);
        budgets.constrain(owner,goalId,scope);
        if(respectPreferences!=null ? respectPreferences : proposal.path("scope").path("respectPreferences").asBoolean(false))
            PlanScheduling.respectPreferences(scope);
        var work = mapper.createObjectNode(); var list = work.putArray("tasks");
        var topicIds = new java.util.HashSet<String>();
        for (String field : List.of("tasks", "unscheduled")) for (var item : proposal.path(field)) {
            var copy = mapper.createObjectNode();
            for (String name : List.of("id", "topicId", "title", "estimatedMinutes", "requiresContinuousSession"))
                if (item.has(name)) copy.set(name, item.get(name));
            list.add(copy); topicIds.add(item.path("topicId").asText());
        }
        var selected = mapper.createArrayNode();
        scope.path("selectedTopics").forEach(t -> { if (topicIds.contains(t.path("id").asText())) selected.add(t); });
        if (selected.size() != topicIds.size()) throw new PlanException(409,"TOPICS_CHANGED","Chủ đề đã thay đổi. Hãy mở lại cấu hình phù hợp trước khi xếp lại các việc này.");
        scope.set("selectedTopics", selected);
        var result = PlanScheduling.schedule(mapper, work, scope, goals.listScheduledTasksOwned(owner, goalId));
        if (proposal.path("backlogOnly").asBoolean(false)) result.put("backlogOnly", true);
        goals.bumpVersion(owner, goalId);
        drafts.replaceProposal(draftId, goal.version()+1, result.toString(), clock.instant().plus(Duration.ofHours(24)));
        return response(drafts.findDraft(owner, goalId, draftId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public DraftResponse get(UUID owner, UUID goalId, UUID draftId) {
        var draft = drafts.findDraft(owner, goalId, draftId).orElseThrow(GoalService.GoalNotFoundException::new);
        return readResponse(owner,goalId,draft);
    }

    @Transactional(readOnly = true)
    public DraftResponse latest(UUID owner, UUID goalId) {
        goals.findOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        var draft = drafts.findLatestDraft(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        return readResponse(owner,goalId,draft);
    }

    private DraftResponse readResponse(UUID owner, UUID goalId, PlanDraftRepository.StoredDraft draft) {
        var value=response(draft);
        String status = drafts.confirmed(owner,draft.id()) ? "CONFIRMED"
            : !value.proposal().path("backlogOnly").asBoolean(false) && !clock.instant().isBefore(draft.expiresAt()) ? "EXPIRED"
            : goalService.getGoal(owner,goalId).version()!=draft.goalVersion() ? "STALE" : "DRAFT";
        return new DraftResponse(value.id(),value.goalId(),value.goalVersion(),value.proposal(),value.createdAt(),value.expiresAt(),status);
    }

    @Transactional
    public Confirmation confirm(UUID owner, UUID goalId, UUID draftId, UUID key, JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("body", "invalid_type");
        body.fieldNames().forEachRemaining(name -> {
            if (!name.equals("expectedGoalVersion") && !name.equals("tasks") && !name.equals("allowPartial") && !name.equals("deferredTasks") && !name.equals("allowPreferenceExceedance")) throw invalid(name, "unknown_field");
        });
        if (body.has("allowPartial") && !body.get("allowPartial").isBoolean()) throw invalid("allowPartial", "invalid_type");
        boolean allowPartial = body.path("allowPartial").asBoolean(false);
        if(body.has("allowPreferenceExceedance") && !body.path("allowPreferenceExceedance").isBoolean())
            throw invalid("allowPreferenceExceedance","invalid_type");
        if (body.has("deferredTasks") && (!allowPartial || !body.get("deferredTasks").isArray())) throw invalid("deferredTasks", "invalid_type");
        var version = body.get("expectedGoalVersion");
        if (version == null || version.isNull()) throw invalid("expectedGoalVersion", "required");
        if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() < 0)
            throw invalid("expectedGoalVersion", "invalid_type");
        // Scoped task metadata is validated against the owned draft after acquiring the goal lock.
        // Serialize confirmation keys per user, then budget-dependent writes per goal.
        drafts.lockOwner(owner);
        var goal = goals.lockOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        var draft = drafts.findDraft(owner, goalId, draftId).orElseThrow(GoalService.GoalNotFoundException::new);
        var proposal = response(draft).proposal();
        boolean scoped = proposal.has("scope");
        JsonNode taskBody = body.get("tasks");
        var plainTasks = taskBody == null ? null : taskBody.deepCopy();
        if (scoped && plainTasks != null && plainTasks.isArray()) for (var task : plainTasks) if (task.isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) task).remove(List.of("id", "topicId"));
        }
        var input = parseTasks(plainTasks);
        if (allowPartial && !scoped) throw invalid("allowPartial", "scoped_draft_required");
        JsonNode fingerprintMetadata = scoped ? taskBody : null;
        if (allowPartial) {
            var decision = mapper.createObjectNode().put("allowPartial", true);
            decision.set("tasks", taskBody);
            if (body.has("deferredTasks")) decision.set("deferredTasks", body.get("deferredTasks"));
            fingerprintMetadata = decision;
        }
        if(body.path("allowPreferenceExceedance").asBoolean(false)) {
            var decision=mapper.createObjectNode().put("allowPreferenceExceedance",true);
            if(fingerprintMetadata!=null)decision.set("selection",fingerprintMetadata);
            fingerprintMetadata=decision;
        }
        String fingerprint = fingerprint(goalId, draftId, version.intValue(), input, fingerprintMetadata);
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
        validate(goal,owner,input);
        var currentScope=preferenceScope(owner,goal);
        var existing=goals.listScheduledTasksOwned(owner,goalId);
        if (scoped) {
            var checked=(com.fasterxml.jackson.databind.node.ObjectNode)proposal.deepCopy();
            if(proposal.path("scope").path("respectPreferences").asBoolean(false))PlanScheduling.respectPreferences(currentScope);
            checked.set("scope",currentScope);
            budgets.checkPlan(owner,input);
            PlanScheduling.validateConfirmation(taskBody, checked, existing,
                LocalDate.now(clock.withZone(java.time.ZoneId.of(goal.timezone()))), allowPartial);
        }
        var excess=PlanScheduling.preferenceExcess(mapper,taskBody,currentScope,existing);
        if(!excess.isEmpty() && !body.path("allowPreferenceExceedance").asBoolean(false))
            throw new PlanException(422,"PREFERENCE_EXCEEDANCE_REQUIRES_CONFIRMATION",
                "Bản nháp vượt mức mong muốn của môn. Hãy đồng ý phần vượt hoặc xếp lại theo mức mong muốn.",
                Map.of("preferenceExcessDays",excess));
        Map<String, JsonNode> deferred = new HashMap<>();
        if (body.has("deferredTasks")) {
            Map<String, JsonNode> expected = new HashMap<>();
            for (String field : List.of("tasks", "unscheduled")) proposal.path(field).forEach(t -> expected.put(t.path("id").asText(),t));
            var seen = new java.util.HashSet<String>(); taskBody.forEach(t -> seen.add(t.path("id").asText()));
            for (var item : body.path("deferredTasks")) {
                PlanScheduling.strict(item, java.util.Set.of("id","topicId","title","estimatedMinutes"));
                String id = PlanScheduling.text(item,"id",100);
                String topic = PlanScheduling.text(item,"topicId",100);
                PlanScheduling.text(item,"title",200);
                var minutes = item.path("estimatedMinutes");
                if (!seen.add(id) || !expected.containsKey(id) || !expected.get(id).path("topicId").asText().equals(topic)
                    || !minutes.isIntegralNumber() || !minutes.canConvertToInt() || minutes.asInt()<1 || minutes.asInt()>480)
                    throw invalid("deferredTasks","invalid_task");
                deferred.put(id,item);
            }
            if (seen.size()!=expected.size()) throw invalid("deferredTasks","missing_tasks");
        }
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < input.size(); i++) {
            UUID taskId = tasks.insertOwned(owner, goalId, input.get(i), "ai").id(); ids.add(taskId);
            if (scoped) {
                String topicId = taskBody.get(i).path("topicId").asText();
                var topic = java.util.stream.StreamSupport.stream(proposal.path("scope").path("selectedTopics").spliterator(), false)
                        .filter(t -> t.path("id").asText().equals(topicId)).findFirst().orElseThrow();
                tasks.attachTopicOwned(owner, goalId, taskId, topicId, topic.path("title").asText());
            }
        }
        goals.bumpVersion(owner, goalId);
        UUID remainingId = null;
        int remainingCount = 0;
        if (scoped && allowPartial) {
            var selected = new java.util.HashSet<String>();
            taskBody.forEach(t -> selected.add(t.path("id").asText()));
            var remainder = ((com.fasterxml.jackson.databind.node.ObjectNode) proposal).deepCopy();
            var pending = mapper.createArrayNode();
            for (String field : List.of("tasks", "unscheduled")) for (var item : proposal.path(field)) {
                if (!selected.contains(item.path("id").asText())) {
                    var value = ((com.fasterxml.jackson.databind.node.ObjectNode) item).deepCopy();
                    var edited = deferred.get(item.path("id").asText());
                    if (edited != null) { value.set("title",edited.get("title")); value.set("estimatedMinutes",edited.get("estimatedMinutes")); }
                    value.putNull("scheduledDate");
                    if (!value.hasNonNull("reason")) {
                        value.put("reasonCode", "DEFERRED_BY_USER");
                        value.put("reason", "Chưa đưa vào lịch. Hãy điều chỉnh quỹ phút hoặc chọn ngày phù hợp.");
                    }
                    pending.add(value);
                }
            }
            remainingCount = pending.size();
            if (remainingCount > 0) {
                remainder.putArray("tasks"); remainder.set("unscheduled", pending);
                remainder.put("backlogOnly", true);
                int available = PlanScheduling.remaining(proposal.path("scope"), goals.listScheduledTasksOwned(owner, goalId)).values().stream().mapToInt(Integer::intValue).sum();
                int required = 0; for (var item : pending) required += item.path("estimatedMinutes").asInt();
                remainder.putObject("summary").put("requiredMinutes", required).put("availableMinutes", available)
                    .put("shortfallMinutes", Math.max(0, required - available)).put("canConfirm", false);
                remainingId = drafts.insertDraft(goalId, goal.version() + 1, remainder.toString(), clock.instant().plus(Duration.ofHours(24))).id();
            }
        }
        var result = new ConfirmResponse(draftId, goalId, goal.version() + 1, ids.size(), List.copyOf(ids), remainingId, remainingCount);
        try { drafts.saveReceipt(owner, key, draftId, fingerprint, mapper.writeValueAsString(result)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
        return new Confirmation(result, false);
    }

    private void requireUsable(UUID owner, PlanDraftRepository.StoredDraft draft) {
        if (drafts.confirmed(owner, draft.id()))
            throw new PlanException(409, "DRAFT_ALREADY_CONFIRMED", "Bản nháp này đã được lưu thành lịch học. Bạn có thể xem lại nhưng không thể lưu lần nữa.");
        if (!response(draft).proposal().path("backlogOnly").asBoolean(false) && !clock.instant().isBefore(draft.expiresAt()))
            throw new PlanException(410, "DRAFT_EXPIRED", "Bản nháp đã hết hạn xác nhận. Hãy tạo bản nháp mới; lịch đã lưu được giữ nguyên.");
    }

    private void validate(GoalJdbcRepository.StoredGoal goal, UUID owner, List<TaskPolicy.TaskInput> input) {
        var violations = new ArrayList<TaskValidationException.FieldViolation>();
        for (var task : input) {
            violations.addAll(TaskPolicy.validateInput(task));
            violations.addAll(TaskPolicy.validateWithinGoal(task.scheduledDate(), goal.startDate(), goal.examDate()));
        }
        if (!violations.isEmpty()) throw new TaskValidationException(violations);
        budgets.checkPlan(owner,input);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode preferenceScope(UUID owner, GoalJdbcRepository.StoredGoal goal) {
        var configured=GoalPlanning.configured(goal) ? goal.planningScope() : GoalPlanning.defaults(goal.topics());
        var scope=PlanScheduling.scope(mapper,configured,goal,LocalDate.now(clock.withZone(java.time.ZoneId.of(goal.timezone()))));
        budgets.constrain(owner,goal.id(),scope);
        return scope;
    }

    private List<TaskPolicy.TaskInput> parseTasks(JsonNode list) {
        return parseTasks(list, false);
    }

    private List<TaskPolicy.TaskInput> parseTasks(JsonNode list, boolean allowPriority) {
        if (list == null || !list.isArray() || list.isEmpty()) throw invalid("tasks", "required");
        var result = new ArrayList<TaskPolicy.TaskInput>();
        for (var node : list) {
            JsonNode fields = node;
            if (allowPriority && node.isObject() && node.has("priority")) {
                JsonNode priority = node.get("priority");
                if (!priority.isTextual() || !java.util.Set.of("HIGH", "MEDIUM", "LOW").contains(priority.textValue()))
                    throw invalid("priority", "invalid_type");
                var copy = ((com.fasterxml.jackson.databind.node.ObjectNode) node).deepCopy();
                copy.remove("priority");
                fields = copy;
            }
            var parsed = TaskRequest.parse(fields, true);
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

    private String fingerprint(UUID goalId, UUID draftId, int version, List<TaskPolicy.TaskInput> input, JsonNode metadata) {
        try {
            byte[] canonical = mapper.writeValueAsBytes(metadata == null ? List.of(goalId, draftId, version, input) : List.of(goalId, draftId, version, input, metadata));
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
