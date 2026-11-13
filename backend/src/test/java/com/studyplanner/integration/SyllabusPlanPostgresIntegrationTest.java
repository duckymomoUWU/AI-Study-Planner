package com.studyplanner.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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
import com.fasterxml.jackson.databind.node.*;
import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.goals.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "goal.integration", matches = "true")
class SyllabusPlanPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockBean GeminiPlanService generator;
    UUID owner, stranger, goal;
    LocalDate today;
    @BeforeEach void setup() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().endsWith("study_planner_test"), "Disposable test DB required");
        }
        jdbc.update("DELETE FROM app_users");
        owner = UUID.randomUUID(); stranger = UUID.randomUUID(); goal = UUID.randomUUID();
        today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for (var id : List.of(owner,stranger)) jdbc.update("INSERT INTO app_users (id,email,password_hash,study_daily_minutes) VALUES (?,?,?,1440)", id, id+"@example.test", "unused");
        jdbc.update("INSERT INTO study_goals (id,user_id,subject,topics,start_date,exam_date,minutes_per_day) VALUES (?,?,?,?,?,?,?)", goal,owner,"Game","Collision, Path finding",today,today.plusDays(3),60);
        when(generator.generateProposalJson(any())).thenReturn(proposal(40,40));
    }
    String base() { return "/api/v1/goals/"+goal+"/plan-drafts"; }
    ResultActions call(UUID user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        var p = new AppUserPrincipal(user,user+"@example.test","unused");
        return mvc.perform(request.with(authentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    JsonNode data(ResultActions result) throws Exception { return mapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data"); }
    Map<String,Object> scope() { return Map.of("selectedTopics",List.of(Map.of("id","collision","title","Collision"),Map.of("id","path","title","Path finding"))); }
    String proposal(int a,int b) throws Exception { return mapper.writeValueAsString(Map.of("tasks",List.of(
            Map.of("id","a","topicId","collision","title","Explain collision with an example","estimatedMinutes",a),
            Map.of("id","b","topicId","path","title","Trace path finding on a graph","estimatedMinutes",b)))); }
    ObjectNode confirmBody(JsonNode draft) {
        var body=mapper.createObjectNode(); body.put("expectedGoalVersion",draft.path("goalVersion").asInt());
        var items=body.putArray("tasks"); draft.at("/proposal/tasks").forEach(t -> { var task=((ObjectNode)t).deepCopy(); task.remove(List.of("priority","requiresContinuousSession")); items.add(task); });
        return body;
    }
    ResultActions confirm(UUID user,JsonNode draft,String key,Object body) throws Exception { return call(user,post(base()+"/"+draft.path("id").asText()+"/confirm").header("Idempotency-Key",key),body); }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class); }
    @Test void selectedTopicsPersistOnlyAfterConfirmWithOwnerAndReplayProtection() throws Exception {
        var draft=data(call(owner,post(base()),scope()).andExpect(status().isCreated()));
        assertEquals(0,count("study_tasks"));
        call(stranger,post(base()),scope()).andExpect(status().isNotFound());
        var body=confirmBody(draft); String key=UUID.randomUUID().toString();
        confirm(stranger,draft,key,body).andExpect(status().isNotFound());
        confirm(owner,draft,key,body).andExpect(status().isCreated()).andExpect(jsonPath("$.data.createdCount").value(2));
        confirm(owner,draft,key,body).andExpect(status().isOk());
        assertEquals(2,count("study_tasks")); assertEquals(1,count("plan_confirmations"));
        assertEquals(Set.of("collision","path"),new HashSet<>(jdbc.queryForList("SELECT topic_id FROM study_tasks",String.class)));
        call(owner,get("/api/v1/goals/"+goal+"/tasks"),Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topicId").value("collision"));
    }
    @Test void invalidEditsRollbackAndKeepSavedScheduleAndDraft() throws Exception {
        jdbc.update("INSERT INTO study_tasks(goal_id,title,scheduled_date,estimated_minutes) VALUES (?,?,?,?)",goal,"Saved work",today.plusDays(2),15);
        var draft=data(call(owner,post(base()),scope()).andExpect(status().isCreated()));
        var body=confirmBody(draft); body.path("tasks").forEach(t -> ((ObjectNode)t).put("scheduledDate",today.toString()));
        confirm(owner,draft,UUID.randomUUID().toString(),body).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PREFERENCE_EXCEEDANCE_REQUIRES_CONFIRMATION"));
        assertEquals(1,count("study_tasks")); assertEquals(0,count("plan_confirmations")); assertEquals(1,count("plan_drafts"));
        // Saving planning settings before AI advances the goal version; a failed confirm must not advance it again.
        assertEquals(draft.path("goalVersion").asInt(),jdbc.queryForObject("SELECT version FROM study_goals WHERE id=?",Integer.class,goal));
        ((ObjectNode)body.path("tasks").get(1)).put("scheduledDate",today.plusDays(1).toString()).put("topicId","unselected");
        confirm(owner,draft,UUID.randomUUID().toString(),body).andExpect(status().isBadRequest());
        assertEquals(1,count("study_tasks"));
    }
    @Test void partialDraftKeepsEveryTaskAndCannotBeConfirmedByDroppingUnscheduledWork() throws Exception {
        jdbc.update("UPDATE app_users SET study_daily_minutes=120 WHERE id=?",owner);
        jdbc.update("UPDATE study_goals SET exam_date=?,minutes_per_day=120 WHERE id=?",today.plusDays(1),goal);
        when(generator.generateProposalJson(any())).thenReturn(proposal(90,90));
        var draft=data(call(owner,post(base()),scope()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.proposal.summary.shortfallMinutes").value(60))
                .andExpect(jsonPath("$.data.proposal.unscheduled.length()").value(1)));
        confirm(owner,draft,UUID.randomUUID().toString(),confirmBody(draft)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PARTIAL_PLAN_REQUIRES_DECISION"));
        assertEquals(0,count("study_tasks"));
    }
    @Test void invalidProviderAndProviderFailureNeverReplaceOldDraftOrSavedTasks() throws Exception {
        var first=data(call(owner,post(base()),scope()).andExpect(status().isCreated()));
        jdbc.update("INSERT INTO study_tasks(goal_id,title,scheduled_date,estimated_minutes) VALUES (?,?,?,?)",goal,"Saved work",today.plusDays(2),15);
        for (String raw : List.of(proposal(-2,15),proposal(30,30).replace("\"collision\"","\"unselected\""),
                proposal(30,30).replace("\"id\":\"a\"","\"scheduledDate\":\""+today.plusDays(3)+"\",\"id\":\"a\""),
                proposal(30,30).replace("\"id\":\"a\"","\"scheduledDate\":\""+today.plusDays(4)+"\",\"id\":\"a\""))) {
            when(generator.generateProposalJson(any())).thenReturn(raw);
            call(owner,post(base()),scope()).andExpect(status().isBadGateway());
        }
        when(generator.generateProposalJson(any())).thenThrow(new PlanException(503,"AI_UNAVAILABLE","Try again"));
        call(owner,post(base()),scope()).andExpect(status().isServiceUnavailable());
        assertEquals(1,count("plan_drafts")); assertEquals(1,count("study_tasks"));
        call(owner,get(base()+"/latest"),Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(first.path("id").asText()));
    }
    @Test void noStudyDaysOrAllZeroAvailabilityNeverCallsAi() throws Exception {
        clearInvocations(generator);
        var body=new HashMap<String,Object>(scope()); body.put("dailyMinutes",Map.of(today.toString(),0,today.plusDays(1).toString(),0,today.plusDays(2).toString(),0));
        call(owner,post(base()),body).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("NO_STUDY_TIME"));
        jdbc.update("UPDATE study_goals SET start_date=?,exam_date=? WHERE id=?",today.minusDays(1),today,goal);
        call(owner,post(base()),scope()).andExpect(status().isUnprocessableEntity());
        verify(generator,never()).generateProposalJson(any()); assertEquals(0,count("plan_drafts"));
    }
    @Test void changingExamWhileAiIsRunningRejectsOldResult() throws Exception {
        var started=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(generator.generateProposalJson(any())).thenAnswer(invocation -> { started.countDown(); assertTrue(release.await(10,TimeUnit.SECONDS)); return proposal(30,30); });
        try (var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(() -> call(owner,post(base()),scope()).andReturn().getResponse().getStatus());
            assertTrue(started.await(10,TimeUnit.SECONDS));
            try { jdbc.update("UPDATE study_goals SET exam_date=?,version=version+1 WHERE id=?",today.plusDays(4),goal); }
            finally { release.countDown(); }
            assertEquals(409,pending.get(10,TimeUnit.SECONDS));
        }
        assertEquals(0,count("plan_drafts")); assertEquals(0,count("study_tasks"));
    }
}
