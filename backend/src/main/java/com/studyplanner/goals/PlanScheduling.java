package com.studyplanner.goals;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.api.RequestValidationException;

/** Deterministic date/minute allocator. Estimates remain intact; no implicit splitting. */
public final class PlanScheduling {
    private PlanScheduling() {}
    static JsonNode validateTopics(JsonNode body) {
        if (body == null || !body.isObject()) throw bad("body", "invalid_type");
        strict(body, Set.of("selectedTopics", "dailyMinutes"));
        var topics = body.get("selectedTopics");
        if (topics == null || !topics.isArray() || topics.isEmpty() || topics.size() > 200) throw bad("selectedTopics", "required");
        Set<String> ids = new HashSet<>();
        for (var topic : topics) {
            strict(topic, Set.of("id", "title", "chapter", "sources", "priority"));
            String id = text(topic, "id", 100), title = text(topic, "title", 200);
            if (!id.matches("[A-Za-z0-9_-]+") || !ids.add(id)) throw bad("selectedTopics.id", "duplicate_or_invalid_id");
            if (topic.has("chapter")) text(topic, "chapter", 250);
            if (topic.has("priority") && !Set.of("HIGH", "MEDIUM", "LOW").contains(topic.path("priority").asText())) throw bad("priority", "invalid_type");
            if (topic.has("sources")) {
                if (!topic.get("sources").isArray() || topic.get("sources").size() > 100) throw bad("sources", "invalid_type");
                for (var source : topic.get("sources")) {
                    strict(source, Set.of("page", "section"));
                    if (source.hasNonNull("page") && (!source.path("page").isIntegralNumber() || source.path("page").asInt() < 1 || source.path("page").asInt() > 100)) throw bad("sources.page", "invalid_type");
                    text(source, "section", 250);
                }
            }
        }
        return topics;
    }
    public static ObjectNode scope(ObjectMapper mapper, JsonNode body, GoalJdbcRepository.StoredGoal goal, LocalDate today) {
        var topics=validateTopics(body);
        ObjectNode scope = mapper.createObjectNode(); scope.set("selectedTopics", topics.deepCopy());
        ObjectNode budgets = scope.putObject("dailyMinutes");
        if (!goal.examDate().isAfter(goal.startDate()) || !goal.examDate().isAfter(today)) throw noDays();
        LocalDate first = goal.startDate().isBefore(today) ? today : goal.startDate();
        first.datesUntil(goal.examDate()).forEach(day -> budgets.put(day.toString(), goal.minutesPerDay()));
        if (body.has("dailyMinutes")) {
            JsonNode custom = body.get("dailyMinutes");
            if (!custom.isObject() || custom.size() > 90) throw bad("dailyMinutes", "invalid_type");
            custom.fields().forEachRemaining(entry -> {
                LocalDate day = date(entry.getKey()); JsonNode value = entry.getValue();
                if (day.isBefore(goal.startDate()) || !day.isBefore(goal.examDate())) throw bad("dailyMinutes", "invalid_date");
                if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0 || value.intValue() > goal.minutesPerDay()) throw bad("dailyMinutes", "out_of_range");
                if (!day.isBefore(first)) budgets.set(day.toString(), value);
            });
        }
        if (!budgets.elements().hasNext() || java.util.stream.StreamSupport.stream(budgets.spliterator(), false).mapToInt(JsonNode::asInt).sum() == 0) throw noDays();
        scope.put("assumption", "Dùng ưu tiên do bạn chọn; cùng mức ưu tiên dùng thứ tự đề cương. Chỉ có tổng phút theo ngày, chưa có khung giờ liên tục.");
        return scope;
    }
    public static ObjectNode schedule(ObjectMapper mapper, JsonNode raw, ObjectNode scope,
            List<GoalPolicy.ScheduledTask> existing) {
        try {
            strict(raw, Set.of("tasks"));
            var input = raw.get("tasks");
            if (input == null || !input.isArray() || input.isEmpty() || input.size() > 1000) throw invalidAi("INVALID_TASK_LIST");
            Map<String, JsonNode> topics = new LinkedHashMap<>();
            scope.get("selectedTopics").forEach(t -> topics.put(t.path("id").asText(), t));
            Map<String, Integer> remaining = remaining(scope, existing);
            int available = remaining.values().stream().mapToInt(Integer::intValue).sum();
            List<ObjectNode> candidates = new ArrayList<>(); Set<String> ids = new HashSet<>(), duplicates = new HashSet<>(), covered = new HashSet<>();
            Set<String> existingTitles = new HashSet<>(); existing.forEach(t -> existingTitles.add(normalize(t.title())));
            long required = 0; int alreadyScheduled = 0;
            for (var node : input) {
                strict(node, Set.of("id", "topicId", "title", "estimatedMinutes", "scheduledDate", "requiresContinuousSession"));
                String id = text(node, "id", 100), topicId = text(node, "topicId", 100), title = text(node, "title", 200);
                int minutes = minutes(node);
                if (!id.matches("[A-Za-z0-9_-]+") || !ids.add(id)) throw invalidAi("INVALID_OR_DUPLICATE_ID");
                if (!topics.containsKey(topicId)) throw invalidAi("UNKNOWN_TOPIC");
                if (!duplicates.add(topicId + ":" + normalize(title))) throw invalidAi("DUPLICATE_WORK");
                if (normalize(title).equals(normalize(topics.get(topicId).path("title").asText()))) throw invalidAi("TITLE_IS_ONLY_TOPIC");
                if (node.has("requiresContinuousSession") && !node.get("requiresContinuousSession").isBoolean()) throw new IllegalArgumentException();
                if (node.has("scheduledDate") && !node.get("scheduledDate").isNull() && (!node.get("scheduledDate").isTextual() || !remaining.containsKey(node.get("scheduledDate").textValue()))) throw new IllegalArgumentException();
                // Existing work remains in the saved calendar; do not recreate it or reject other new work.
                if (existingTitles.contains(normalize(title))) { covered.add(topicId); alreadyScheduled++; continue; }
                ObjectNode task = ((ObjectNode) node).deepCopy(); task.put("requiresContinuousSession", node.path("requiresContinuousSession").asBoolean(true));
                task.put("priority", topics.get(topicId).path("priority").asText("MEDIUM"));
                candidates.add(task); covered.add(topicId); required += minutes;
            }
            if (!covered.containsAll(topics.keySet())) throw invalidAi("MISSING_TOPICS");
            if (candidates.isEmpty() && alreadyScheduled > 0) throw new PlanException(422,"PLAN_ALREADY_SAVED","Các công việc AI đề xuất đều đã có trong lịch học. Hãy xem lịch hiện tại; không cần lưu lại.");
            List<String> order = new ArrayList<>(topics.keySet());
            candidates.sort(Comparator.<ObjectNode>comparingInt(t -> rank(t.path("priority").asText())).thenComparingInt(t -> order.indexOf(t.path("topicId").asText())));
            ObjectNode result = mapper.createObjectNode(); result.set("scope", scope); var scheduled = result.putArray("tasks"); var unscheduled = result.putArray("unscheduled");
            for (var task : candidates) {
                String chosen = null;
                boolean requestedDay = task.hasNonNull("scheduledDate");
                if (task.hasNonNull("scheduledDate")) {
                    String requested = task.get("scheduledDate").asText();
                    if (remaining.get(requested) >= minutes(task)) chosen = requested;
                } else {
                    for (var entry : remaining.entrySet()) if (entry.getValue() >= minutes(task)) { chosen = entry.getKey(); break; }
                }
                if (chosen == null) {
                    task.putNull("scheduledDate");
                    task.put("reasonCode", requestedDay ? "REQUESTED_DAY_FULL" : required > available ? "TOTAL_BUDGET_SHORTFALL" : "NO_DAY_FITS");
                    task.put("reason", requestedDay ? "Ngày AI đề xuất không đủ phút còn lại; hãy chọn một ngày phù hợp khác." : required > available ? "Tổng thời gian cần ôn vượt quỹ phút còn lại." : "Không có ngày phù hợp còn đủ phút cho việc này; hệ thống không tự chia nhỏ việc.");
                    unscheduled.add(task);
                } else { task.put("scheduledDate", chosen); remaining.put(chosen, remaining.get(chosen) - minutes(task)); scheduled.add(task); }
            }
            var summary = result.putObject("summary"); summary.put("requiredMinutes", required); summary.put("availableMinutes", available);
            summary.put("shortfallMinutes", Math.max(0, required - available)); summary.put("canConfirm", unscheduled.isEmpty());
            summary.put("alreadyScheduledCount",alreadyScheduled);
            return result;
        } catch (Exception ex) {
            if (ex instanceof PlanException p) throw p;
            throw invalidAi("INVALID_FIELDS");
        }
    }
    public static void validateConfirmation(JsonNode list, JsonNode proposal, List<GoalPolicy.ScheduledTask> existing, LocalDate today) {
        validateConfirmation(list, proposal, existing, today, false);
    }
    public static void validateConfirmation(JsonNode list, JsonNode proposal, List<GoalPolicy.ScheduledTask> existing, LocalDate today, boolean allowPartial) {
        if (list == null || !list.isArray() || list.isEmpty()) throw bad("tasks", "required");
        Map<String, JsonNode> expected = new HashMap<>(); proposal.path("tasks").forEach(t -> expected.put(t.path("id").asText(), t));
        proposal.path("unscheduled").forEach(t -> expected.put(t.path("id").asText(), t));
        if (!allowPartial && list.size() != expected.size()) throw new PlanException(422, "PARTIAL_PLAN_REQUIRES_DECISION", "Hãy chọn lưu phần đã xếp để giữ lại các việc còn lại.");
        Map<String, Integer> remaining = remaining(proposal.get("scope"), existing); Set<String> seen = new HashSet<>(), duplicates = new HashSet<>();
        Set<String> savedTitles = new HashSet<>(); existing.forEach(t -> savedTitles.add(normalize(t.title())));
        for (var task : list) {
            strict(task, Set.of("id", "topicId", "title", "scheduledDate", "estimatedMinutes"));
            String id = text(task, "id", 100), topic = text(task, "topicId", 100), title = text(task, "title", 200);
            if (!seen.add(id) || !expected.containsKey(id) || !expected.get(id).path("topicId").asText().equals(topic) || !duplicates.add(topic + ":" + normalize(title)) || savedTitles.contains(normalize(title))) throw bad("tasks", "unknown_or_duplicate_task");
            int minutes = minutes(task);
            String day = text(task, "scheduledDate", 10);
            if (date(day).isBefore(today) || !remaining.containsKey(day)) throw bad("scheduledDate", "invalid_date");
            int left = remaining.get(day);
            if (left < minutes) throw new PlanException(422, "DAILY_BUDGET_EXCEEDED", "Ngày đã chọn không đủ phút học.", Map.of("scheduledDate", day, "excessMinutes", minutes - left));
            remaining.put(day, left - minutes);
        }
    }
    public static Map<String, Integer> remaining(JsonNode scope, List<GoalPolicy.ScheduledTask> existing) {
        Map<String, Integer> result = new TreeMap<>(); scope.path("dailyMinutes").fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asInt()));
        existing.forEach(t -> result.computeIfPresent(t.scheduledDate().toString(), (d, value) -> Math.max(0, value - t.estimatedMinutes())));
        return result;
    }
    private static int rank(String value) { return value.equals("HIGH") ? 0 : value.equals("LOW") ? 2 : 1; }
    private static PlanException invalidAi(String reason) {
        String message = switch(reason) {
            case "MISSING_TOPICS" -> "AI chưa tạo đủ việc cho các chủ đề đã chọn.";
            case "UNKNOWN_TOPIC" -> "AI gắn công việc vào chủ đề không nằm trong phạm vi đã chọn.";
            case "INVALID_OR_DUPLICATE_ID", "DUPLICATE_WORK" -> "AI trả công việc bị trùng hoặc mã công việc không hợp lệ.";
            case "TITLE_IS_ONLY_TOPIC" -> "AI chỉ trả tên chủ đề, chưa mô tả việc học cụ thể.";
            default -> "AI trả sai cấu trúc công việc, số phút hoặc ngày học.";
        };
        return new PlanException(502,"AI_INVALID_RESPONSE",message+" Hãy thử lại; lịch đã lưu không thay đổi.",Map.of("reasonCode",reason));
    }
    private static int minutes(JsonNode task) {
        var n = task.get("estimatedMinutes");
        if (n == null || !n.isIntegralNumber() || !n.canConvertToInt() || n.intValue() < 1 || n.intValue() > 480) throw bad("estimatedMinutes", "out_of_range");
        return n.intValue();
    }
    private static LocalDate date(String value) { try { return LocalDate.parse(value); } catch (Exception ex) { throw bad("scheduledDate", "invalid_date"); } }
    private static String normalize(String value) { return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    static String text(JsonNode node, String name, int max) {
        var value = node.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max) throw bad(name, "invalid_type");
        return value.textValue().strip();
    }
    static void strict(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw bad("body", "invalid_type");
        node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw bad(field, "unknown_field"); });
    }
    private static PlanException noDays() { return new PlanException(422, "NO_STUDY_TIME", "Không còn ngày học hợp lệ hoặc tổng quỹ phút bằng 0. Hãy chỉnh ngày thi và thời gian học."); }
    private static RequestValidationException bad(String field, String issue) { return new RequestValidationException(List.of(new ErrorDetail(field, issue))); }
}
