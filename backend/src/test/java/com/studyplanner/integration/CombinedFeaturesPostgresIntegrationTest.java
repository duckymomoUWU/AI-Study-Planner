package com.studyplanner.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.databind.*;
import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.goals.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "goal.integration", matches = "true")
class CombinedFeaturesPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockBean GeminiPlanService generator;
    UUID owner, stranger, goal;
    LocalDate today;

    @BeforeEach void reset() throws Exception {
        jdbc.update("DELETE FROM app_users");
        owner = UUID.randomUUID(); stranger = UUID.randomUUID(); goal = UUID.randomUUID();
        today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for (UUID id : List.of(owner, stranger)) jdbc.update(
                "INSERT INTO app_users (id,email,password_hash,study_daily_minutes) VALUES (?,?,?,1440)", id, id + "@example.test", "unused-hash");
        jdbc.update("INSERT INTO study_goals (id,user_id,subject,topics,start_date,exam_date,minutes_per_day) VALUES (?,?,?,?,?,?,?)",
                goal, owner, "SQL", "JOIN, Index", today.minusDays(1), today.plusDays(3), 60);
        when(generator.generateProposalJson(any())).thenReturn(mapper.writeValueAsString(Map.of("tasks", List.of(item(today, 30)))));
    }
    MockHttpServletRequestBuilder as(UUID user, MockHttpServletRequestBuilder request) {
        var principal = new AppUserPrincipal(user, user + "@example.test", "unused-hash");
        return request.with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))).with(csrf());
    }
    Map<String,Object> item(LocalDate day, int minutes) { return Map.of("title", "Study SQL", "scheduledDate", day.toString(), "estimatedMinutes", minutes); }
    String taskUrl() { return "/api/v1/goals/" + goal + "/tasks"; }
    String draftUrl() { return "/api/v1/goals/" + goal + "/plan-drafts"; }
    ResultActions json(UUID user, MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return mvc.perform(as(user, builder).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    JsonNode data(ResultActions result) throws Exception { return mapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data"); }
    String draft() throws Exception { return data(json(owner, post(draftUrl()), Map.of()).andExpect(status().isCreated())).get("id").asText(); }
    Map<String,Object> confirmBody(int version, Object... items) { return Map.of("expectedGoalVersion", version, "tasks", List.of(items)); }
    ResultActions confirm(UUID user, String draft, UUID key, Object body) throws Exception {
        return json(user, post(draftUrl() + "/" + draft + "/confirm").header("Idempotency-Key", key), body);
    }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    int version() { return jdbc.queryForObject("SELECT version FROM study_goals WHERE id = ?", Integer.class, goal); }

    @Test void manualCrudEnforcesOwnerBudgetDatesAndVersionAndKeepsProgress() throws Exception {
        String id = data(json(owner, post(taskUrl()), item(today, 45)).andExpect(status().isCreated())).get("id").asText();
        assertEquals(1, version());
        json(owner, patch(taskUrl() + "/" + id), Map.of("completed", true)).andExpect(status().isOk());
        json(owner, post(taskUrl()), item(today, 16)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DAILY_BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.error.context.excessMinutes").value(1));
        json(stranger, patch("/api/v1/tasks/" + id), Map.of("title", "Foreign update")).andExpect(status().isNotFound());
        json(owner, patch("/api/v1/tasks/" + id), Map.of("scheduledDate", today.plusDays(1).toString(), "estimatedMinutes", 30))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scheduledDate").value(today.plusDays(1).toString()));
        assertEquals(3, version());
        json(owner, patch("/api/v1/tasks/" + id), Map.of("estimatedMinutes", 30)).andExpect(status().isOk());
        assertEquals(3, version());
        mvc.perform(as(owner, get("/api/v1/goals/" + goal + "/progress")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completedTasks").value(1));
        mvc.perform(as(stranger, delete(taskUrl() + "/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(owner, delete(taskUrl() + "/" + id))).andExpect(status().isNoContent());
        assertEquals(4, version()); assertEquals(0, count("study_tasks"));
    }

    @Test void taskInputsAreStrictAndOverdueUsesRealStoredTasks() throws Exception {
        for (Object minutes : List.of("30", 30.5, 0, 481)) {
            json(owner, post(taskUrl()), Map.of("title", "SQL", "scheduledDate", today.toString(), "estimatedMinutes", minutes))
                    .andExpect(status().isBadRequest());
        }
        json(owner, post(taskUrl()), item(today.plusDays(3), 15)).andExpect(status().isBadRequest());
        json(owner, post(taskUrl()), item(today.minusDays(2), 15)).andExpect(status().isBadRequest());
        json(owner, post(taskUrl()), Map.of("title", "SQL", "scheduledDate", today.toString(), "estimatedMinutes", 15, "source", "ai"))
                .andExpect(status().isBadRequest());
        assertEquals(0, count("study_tasks")); assertEquals(0, version());
        json(owner, post(taskUrl()), item(today.minusDays(1), 15)).andExpect(status().isCreated());
        mvc.perform(as(owner, get("/api/v1/tasks/overdue"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(as(stranger, get("/api/v1/tasks/overdue"))).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test void draftIsReadOnlyUntilExplicitConfirmAndRetryReplaysEvenAfterExpiry() throws Exception {
        String id = draft(); assertEquals(0, count("study_tasks")); assertEquals(0, version());
        mvc.perform(as(owner, get(draftUrl() + "/" + id))).andExpect(status().isOk()).andExpect(jsonPath("$.data.proposal.tasks.length()").value(1));
        mvc.perform(as(stranger, get(draftUrl() + "/" + id))).andExpect(status().isNotFound());
        UUID key = UUID.randomUUID(); var body = confirmBody(0, item(today, 25), item(today.plusDays(1), 35));
        JsonNode first = data(confirm(owner, id, key, body).andExpect(status().isCreated()));
        assertEquals(2, first.get("createdCount").intValue()); assertEquals(1, version());
        jdbc.update("UPDATE plan_drafts SET created_at = now() - interval '2 days', expires_at = now() - interval '1 day' WHERE id = ?::uuid", id);
        assertEquals(first, data(confirm(owner, id, key, body).andExpect(status().isOk())));
        assertEquals(2, count("study_tasks")); assertEquals(1, count("plan_confirmations")); assertEquals(1, version());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE source = 'ai'", Integer.class));
        confirm(owner, id, key, confirmBody(0, item(today, 20))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        confirm(owner, id, UUID.randomUUID(), body).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DRAFT_ALREADY_CONFIRMED"));
        confirm(stranger, id, key, body).andExpect(status().isNotFound());
    }

    @Test void failedConfirmRollsBackEveryTaskReceiptAndVersion() throws Exception {
        String id = draft();
        confirm(owner, id, UUID.randomUUID(), confirmBody(0, item(today, 30), item(today, 31)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.context.excessMinutes").value(1));
        confirm(owner, id, UUID.randomUUID(), confirmBody(0, item(today, 30), item(today.plusDays(3), 15))).andExpect(status().isBadRequest());
        confirm(owner, id, UUID.randomUUID(), confirmBody(0, item(today, 241)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("INSUFFICIENT_TIME"))
                .andExpect(jsonPath("$.error.context.shortfallMinutes").value(1));
        assertEquals(0, count("study_tasks")); assertEquals(0, count("plan_confirmations")); assertEquals(0, version());
        json(owner, post(draftUrl() + "/" + id + "/confirm"), confirmBody(0, item(today, 30))).andExpect(status().isBadRequest());
        json(owner, post(taskUrl()), item(today.plusDays(1), 15)).andExpect(status().isCreated());
        confirm(owner, id, UUID.randomUUID(), confirmBody(0, item(today, 30)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GOAL_VERSION_CONFLICT"));
        jdbc.update("UPDATE plan_drafts SET created_at = now() - interval '2 days', expires_at = now() - interval '1 day' WHERE id = ?::uuid", id);
        confirm(owner, id, UUID.randomUUID(), confirmBody(0, item(today, 30)))
                .andExpect(status().isGone()).andExpect(jsonPath("$.error.code").value("DRAFT_EXPIRED"));
        assertEquals(1, count("study_tasks")); assertEquals(0, count("plan_confirmations"));
    }

    @Test void simultaneousConfirmationsCreateOnlyOneSetOfTasks() throws Exception {
        String id = draft(); UUID key = UUID.randomUUID(); var body = confirmBody(0, item(today, 30));
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> confirm(owner, id, key, body).andReturn().getResponse().getStatus();
            var a = executor.submit(call); var b = executor.submit(call);
            var statuses = new ArrayList<>(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)));
            statuses.sort(Integer::compareTo); assertEquals(List.of(200, 201), statuses);
        }
        assertEquals(1, count("study_tasks")); assertEquals(1, count("plan_confirmations")); assertEquals(1, version());
    }

    @Test void aiErrorsAndInvalidOutputNeverSaveTasksAndManualFlowRemainsUsable() throws Exception {
        when(generator.generateProposalJson(any())).thenReturn("not json");
        json(owner, post(draftUrl()), Map.of()).andExpect(status().isBadGateway()).andExpect(jsonPath("$.error.code").value("AI_INVALID_RESPONSE"));
        when(generator.generateProposalJson(any())).thenReturn(mapper.writeValueAsString(Map.of("tasks", List.of(item(today.plusDays(3), 30)))));
        json(owner, post(draftUrl()), Map.of()).andExpect(status().isBadGateway());
        when(generator.generateProposalJson(any())).thenThrow(new PlanException(503, "AI_UNAVAILABLE", "Unavailable"));
        json(owner, post(draftUrl()), Map.of()).andExpect(status().isServiceUnavailable());
        assertEquals(0, count("plan_drafts")); assertEquals(0, count("study_tasks"));
        json(owner, post(taskUrl()), item(today, 15)).andExpect(status().isCreated());
    }

    @Test void priorityMetadataStaysInDraftAndCannotChangeConfirmContract() throws Exception {
        var task = new HashMap<String,Object>(item(today, 30)); task.put("priority", "HIGH");
        when(generator.generateProposalJson(any())).thenReturn(mapper.writeValueAsString(Map.of("tasks", List.of(task))));
        String id = draft();
        mvc.perform(as(owner, get(draftUrl() + "/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposal.tasks[0].priority").value("HIGH"));
        assertEquals(0, count("study_tasks"));
        confirm(owner, id, UUID.randomUUID(), confirmBody(0, task)).andExpect(status().isBadRequest());
        assertEquals(0, count("study_tasks")); assertEquals(0, version());
        confirm(owner, id, UUID.randomUUID(), confirmBody(0, item(today, 30))).andExpect(status().isCreated());
        assertEquals(1, count("study_tasks")); assertEquals(1, version());
    }

    @Test void invalidAiPriorityIsRejectedBeforeSavingADraft() throws Exception {
        for (Object value : List.of("URGENT", 1, List.of("HIGH"))) {
            var task = new HashMap<String,Object>(item(today, 30)); task.put("priority", value);
            when(generator.generateProposalJson(any())).thenReturn(mapper.writeValueAsString(Map.of("tasks", List.of(task))));
            json(owner, post(draftUrl()), Map.of()).andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error.code").value("AI_INVALID_RESPONSE"));
        }
        assertEquals(0, count("plan_drafts")); assertEquals(0, count("study_tasks"));
    }

    @Test void syllabusQuickfillRequiresAuthCsrfAndDoesNotPersistGoalsOrTasks() throws Exception {
        String path = "/api/v1/goals/parse-syllabus";
        var body = Map.of("content", "Subject: Databases\n- SQL JOIN", "fileName", "course.txt");
        var fields = new GeminiPlanService.SyllabusData("Databases", "SQL JOIN", today, today.plusDays(30), 90);
        when(generator.extractSyllabus(any(), any(), any(), any())).thenReturn(new GeminiPlanService.SyllabusExtraction(fields, "local"));
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        var principal = new AppUserPrincipal(owner, "owner@example.test", "unused");
        mvc.perform(post(path).with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        int before = count("study_goals");
        json(owner, post(path), body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("Databases"))
                .andExpect(jsonPath("$.data.extractionMethod").value("local"));
        assertEquals(before, count("study_goals")); assertEquals(0, count("study_tasks"));
        json(owner, post(path), Map.of("content", "text", "userId", "foreign")).andExpect(status().isBadRequest());
    }
}
