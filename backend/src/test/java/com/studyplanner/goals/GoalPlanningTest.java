package com.studyplanner.goals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

class GoalPlanningTest {
    ObjectMapper mapper=new ObjectMapper();LocalDate today=LocalDate.of(2026,11,11);
    GoalPolicy.GoalInput input(int budget) {return new GoalPolicy.GoalInput("Math","Algebra",today,today.plusDays(5),budget);}
    com.fasterxml.jackson.databind.JsonNode scope() throws Exception {
        return mapper.readTree("{\"selectedTopics\":[{\"id\":\"algebra\",\"title\":\"Algebra\",\"priority\":\"HIGH\",\"sources\":[{\"page\":2,\"section\":\"Chapter 1\"}]}],\"dailyMinutes\":{\"2026-11-12\":0,\"2026-11-13\":30,\"2026-11-14\":60}}");
    }
    GoalJdbcRepository.StoredGoal goal(com.fasterxml.jackson.databind.JsonNode saved) {return new GoalJdbcRepository.StoredGoal(UUID.randomUUID(),UUID.randomUUID(),"Math","Algebra",today,today.plusDays(5),60,"Asia/Ho_Chi_Minh",2,Instant.EPOCH,saved);}
    @Test void preservesZeroDayPriorityAndSourceWhileNormalizingDefaults() throws Exception {
        var normalized=GoalPlanning.normalize(scope(),input(60));
        assertEquals(0,normalized.path("dailyMinutes").path("2026-11-12").asInt(-1));
        assertFalse(normalized.path("dailyMinutes").has("2026-11-14"));
        assertEquals("HIGH",normalized.path("selectedTopics").get(0).path("priority").asText());
        assertEquals(2,normalized.at("/selectedTopics/0/sources/0/page").asInt());
        assertEquals(0,GoalPlanning.minutes(goal(normalized),today.plusDays(1)));
        assertEquals(60,GoalPlanning.minutes(goal(normalized),today));
    }
    @Test void unrelatedGoalEditsKeepDailyOverridesAndSourceTopics() throws Exception {
        var normalized=GoalPlanning.normalize(scope(),input(60));
        assertEquals(normalized,GoalPlanning.forPatch(goal(normalized),input(60),null));
        var changed=new GoalPolicy.GoalInput("Math","New topic",today,today.plusDays(5),60);
        var next=GoalPlanning.forPatch(goal(normalized),changed,null);
        assertEquals("New topic",next.path("selectedTopics").get(0).path("title").asText());
        assertEquals(normalized.path("dailyMinutes"),next.path("dailyMinutes"));
    }
    @Test void cannotReduceDayBelowSavedWorkOrMoveStartPastSavedHistory() throws Exception {
        var saved=List.of(new GoalPolicy.ScheduledTask(today.plusDays(1),30));
        assertThrows(PlanException.class,()->GoalPlanning.validateExisting(input(60),scope(),saved));
        var changed=new GoalPolicy.GoalInput("Math","Algebra",today.plusDays(2),today.plusDays(5),60);
        assertThrows(PlanException.class,()->GoalPlanning.validateExisting(changed,null,saved));
    }
}
