package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class PlanSchedulingTest {
    final ObjectMapper mapper = new ObjectMapper();
    final LocalDate today = LocalDate.of(2026, 11, 10);
    GoalJdbcRepository.StoredGoal goal(int days, int budget) { return new GoalJdbcRepository.StoredGoal(UUID.randomUUID(), UUID.randomUUID(), "Game", "Collision, Path finding", today, today.plusDays(days), budget, "Asia/Ho_Chi_Minh", 0, Instant.EPOCH); }
    JsonNode json(String text) throws Exception { return mapper.readTree(text); }
    ObjectNode scope(int days, int budget) throws Exception { return PlanScheduling.scope(mapper, json("""
        {"selectedTopics":[{"id":"collision","title":"Collision"},{"id":"path","title":"Path finding"}]}
        """), goal(days, budget), today); }
    JsonNode tasks(int a, int b) throws Exception { return json("""
        {"tasks":[{"id":"a","topicId":"collision","title":"Explain collision with a worked example","estimatedMinutes":%d,"requiresContinuousSession":true},
        {"id":"b","topicId":"path","title":"Trace a path finding algorithm","estimatedMinutes":%d}]}
        """.formatted(a,b)); }
    @Test void keeps180MinutesOfWorkWithOnly120Available() throws Exception {
        var result = PlanScheduling.schedule(mapper, tasks(90,90), scope(1,120), List.of());
        assertEquals(1, result.path("tasks").size()); assertEquals(1, result.path("unscheduled").size());
        assertEquals(60, result.at("/summary/shortfallMinutes").asInt());
        assertEquals(90, result.at("/unscheduled/0/estimatedMinutes").asInt());
        assertEquals("TOTAL_BUDGET_SHORTFALL", result.at("/unscheduled/0/reasonCode").asText());
    }
    @Test void cannotFitContinuous45IntoThree20MinuteDaysEvenWithEnoughTotal() throws Exception {
        var result = PlanScheduling.schedule(mapper, tasks(45,15), scope(3,20), List.of());
        assertEquals(0, result.at("/summary/shortfallMinutes").asInt());
        assertEquals("NO_DAY_FITS", result.at("/unscheduled/0/reasonCode").asText());
        assertEquals(45, result.at("/unscheduled/0/estimatedMinutes").asInt());
        assertEquals(1, result.path("tasks").size());
    }
    @Test void zeroDaysAreSkippedAndExistingTasksUseBudget() throws Exception {
        var scope = scope(3,60); ((ObjectNode) scope.get("dailyMinutes")).put(today.toString(), 0);
        var existing = new GoalPolicy.ScheduledTask(UUID.randomUUID(), "Existing task", today.plusDays(1), 45);
        var result = PlanScheduling.schedule(mapper, tasks(45,15), scope, List.of(existing));
        assertEquals(today.plusDays(2).toString(), result.at("/tasks/0/scheduledDate").asText());
        assertEquals(today.plusDays(1).toString(), result.at("/tasks/1/scheduledDate").asText());
    }
    @Test void providerChosenOverfullDayStaysUnscheduledWithoutClaimingEveryDayIsFull() throws Exception {
        var input = tasks(30,30);
        input.path("tasks").forEach(t -> ((ObjectNode)t).put("scheduledDate",today.toString()));
        var result=PlanScheduling.schedule(mapper,input,scope(2,40),List.of());
        assertEquals(1,result.path("tasks").size()); assertEquals(1,result.path("unscheduled").size());
        assertEquals("REQUESTED_DAY_FULL",result.at("/unscheduled/0/reasonCode").asText());
        assertEquals(0,result.at("/summary/shortfallMinutes").asInt());
    }
    @Test void rejectsNegativeUnknownDuplicateMissingTopicAndProviderExamDates() throws Exception {
        for (var raw : List.of(tasks(-1,15), tasks(30,30).deepCopy(), tasks(30,30).deepCopy(), tasks(30,30).deepCopy(), tasks(30,30).deepCopy())) {
            if (raw.path("tasks").get(0).path("estimatedMinutes").asInt() >= 0) {
                var task = (ObjectNode) raw.path("tasks").get(0);
                int which = invalidIndex++;
                if (which == 0) task.put("topicId", "unselected");
                if (which == 1) task.put("scheduledDate", today.plusDays(2).toString());
                if (which == 2) task.put("scheduledDate", today.plusDays(3).toString());
                if (which == 3) task.put("id", "b");
            }
            assertEquals("AI_INVALID_RESPONSE", assertThrows(PlanException.class, () -> PlanScheduling.schedule(mapper, raw, scope(2,60), List.of())).code());
        }
        var missing = tasks(30,30); ((com.fasterxml.jackson.databind.node.ArrayNode) missing.get("tasks")).remove(1);
        assertThrows(PlanException.class, () -> PlanScheduling.schedule(mapper, missing, scope(2,60), List.of()));
    }
    int invalidIndex;
    @Test void regeneratingWithAnExistingTaskDoesNotDiscardOtherValidNewWork() throws Exception {
        var existing = new GoalPolicy.ScheduledTask(UUID.randomUUID(),"Explain collision with a worked example",today,30);
        var result = PlanScheduling.schedule(mapper,tasks(30,30),scope(3,60),List.of(existing));
        assertEquals(1,result.path("tasks").size());
        assertEquals("b",result.at("/tasks/0/id").asText());
        assertEquals(1,result.at("/summary/alreadyScheduledCount").asInt());
    }
    @Test void entirelySavedWorkIsAnInformationalOutcomeAndMissingTopicsRemainInvalid() throws Exception {
        var existing=List.of(new GoalPolicy.ScheduledTask(UUID.randomUUID(),"Explain collision with a worked example",today,30),
            new GoalPolicy.ScheduledTask(UUID.randomUUID(),"Trace a path finding algorithm",today.plusDays(1),30));
        assertEquals("PLAN_ALREADY_SAVED",assertThrows(PlanException.class,()->PlanScheduling.schedule(mapper,tasks(30,30),scope(3,60),existing)).code());
        var missing=tasks(30,30);((com.fasterxml.jackson.databind.node.ArrayNode)missing.get("tasks")).remove(1);
        var error=assertThrows(PlanException.class,()->PlanScheduling.schedule(mapper,missing,scope(3,60),List.of()));
        assertEquals("MISSING_TOPICS",error.context().get("reasonCode"));
        assertFalse(error.context().toString().contains("Collision"));
    }
    @Test void confirmationRejectsTwo40MinuteTasksMovedInto60AndPartialRemoval() throws Exception {
        var proposal = PlanScheduling.schedule(mapper, tasks(40,40), scope(2,60), List.of());
        var edited = proposal.path("tasks").deepCopy();
        for (var task : edited) { var object = (ObjectNode)task; object.remove(List.of("priority", "requiresContinuousSession")); object.put("scheduledDate", today.toString()); }
        assertEquals("DAILY_BUDGET_EXCEEDED", assertThrows(PlanException.class, () -> PlanScheduling.validateConfirmation(edited, proposal, List.of(), today)).code());
        ((com.fasterxml.jackson.databind.node.ArrayNode) edited).remove(1);
        assertEquals("PARTIAL_PLAN_REQUIRES_DECISION", assertThrows(PlanException.class, () -> PlanScheduling.validateConfirmation(edited, proposal, List.of(), today)).code());
    }
    @Test void emptyOrElapsedCalendarFailsBeforeAi() throws Exception {
        var body = json("{\"selectedTopics\":[{\"id\":\"c\",\"title\":\"Collision\"}],\"dailyMinutes\":{\"2026-11-10\":0}}");
        assertEquals("NO_STUDY_TIME", assertThrows(PlanException.class, () -> PlanScheduling.scope(mapper, body, goal(1,60), today)).code());
        assertEquals("NO_STUDY_TIME", assertThrows(PlanException.class, () -> PlanScheduling.scope(mapper, body, goal(1,60), today.plusDays(1))).code());
    }
}
