package com.studyplanner.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.goals.*;
import java.time.*;
import java.util.*;

@SpringBootTest(properties={"spring.datasource.url=${planning.test-db-url:jdbc:postgresql://127.0.0.1:5432/study_planner_test}","spring.datasource.username=study_planner","spring.datasource.password=unused-local"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="planning.integration",matches="true")
class PersistentPlanningPostgresTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;
    @MockBean GeminiPlanService ai;
    UUID owner,foreign;LocalDate today;
    @BeforeEach void seed() {
        owner=UUID.randomUUID();foreign=UUID.randomUUID();today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for(var id:List.of(owner,foreign)) jdbc.update("INSERT INTO app_users(id,email,password_hash,study_daily_minutes) VALUES (?,?,?,1440)",id,"persistent-"+id+"@example.test","unused");
    }
    @AfterEach void clean() {for(var id:List.of(owner,foreign)) jdbc.update("DELETE FROM app_users WHERE id=?",id);}
    ResultActions call(UUID user,MockHttpServletRequestBuilder request,Object body) throws Exception {
        var p=new AppUserPrincipal(user,user+"@example.test","unused");
        return mvc.perform(request.with(authentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    JsonNode data(ResultActions action) throws Exception {return mapper.readTree(action.andReturn().getResponse().getContentAsString()).path("data");}
    ObjectNode scope() {
        var s=mapper.createObjectNode();s.putArray("selectedTopics").addObject().put("id","algebra").put("title","Algebra").put("priority","HIGH")
            .putArray("sources").addObject().put("page",2).put("section","Chapter 1");
        s.putObject("dailyMinutes").put(today.plusDays(1).toString(),0).put(today.plusDays(2).toString(),30);return s;
    }
    JsonNode create() throws Exception {
        return data(call(owner,post("/api/v1/goals"),Map.of("subject","Math","topics","Algebra","startDate",today.toString(),"examDate",today.plusDays(6).toString(),"minutesPerDay",60,"planningScope",scope())).andExpect(status().isCreated()));
    }
    String proposal(String title) throws Exception {return mapper.writeValueAsString(Map.of("tasks",List.of(Map.of("id","work1","topicId","algebra","title",title,"estimatedMinutes",40))));}
    @Test void saveReloadMoveRegenerateAndConfirmRespectSameScopeAndKeepCompletedHistory() throws Exception {
        var goal=create();String id=goal.path("id").asText(),base="/api/v1/goals/"+id;
        var done=data(call(owner,post(base+"/tasks"),Map.of("title","Completed work","scheduledDate",today.toString(),"estimatedMinutes",30)).andExpect(status().isCreated()));
        call(owner,patch("/api/v1/tasks/"+done.path("id").asText()),Map.of("completed",true)).andExpect(status().isOk());
        var todo=data(call(owner,post(base+"/tasks"),Map.of("title","Saved pending work","scheduledDate",today.plusDays(2).toString(),"estimatedMinutes",20)).andExpect(status().isCreated()));
        var reloaded=data(call(owner,get(base),Map.of()).andExpect(status().isOk()));
        assertEquals(scope(),reloaded.get("planningScope"));
        call(owner,patch("/api/v1/tasks/"+todo.path("id").asText()),Map.of("scheduledDate",today.plusDays(1).toString())).andExpect(status().isUnprocessableEntity());
        assertEquals(today.plusDays(2),jdbc.queryForObject("SELECT scheduled_date FROM study_tasks WHERE id=?",LocalDate.class,UUID.fromString(todo.path("id").asText())));
        when(ai.generateProposalJson(any())).thenReturn(proposal("Practice algebra exercises"),proposal("Review algebra mistakes"));
        var draft=data(call(owner,post(base+"/plan-drafts"),Map.of()).andExpect(status().isCreated()));
        assertEquals(0,draft.at("/proposal/scope/dailyMinutes/"+today.plusDays(1)).asInt(-1));
        assertEquals("HIGH",draft.at("/proposal/scope/selectedTopics/0/priority").asText());
        assertEquals(2,draft.at("/proposal/scope/selectedTopics/0/sources/0/page").asInt());
        assertEquals(today.plusDays(3).toString(),draft.at("/proposal/tasks/0/scheduledDate").asText());
        var body=mapper.createObjectNode().put("expectedGoalVersion",draft.path("goalVersion").asInt());var items=body.putArray("tasks");
        for(var item:draft.at("/proposal/tasks")) {var value=(ObjectNode)item.deepCopy();value.remove(List.of("priority","requiresContinuousSession"));items.add(value);}
        String key=UUID.randomUUID().toString(),confirm=base+"/plan-drafts/"+draft.path("id").asText()+"/confirm";
        var saved=data(call(owner,post(confirm).header("Idempotency-Key",key),body).andExpect(status().isCreated()));
        call(owner,post(confirm).header("Idempotency-Key",key),body).andExpect(status().isOk());
        String newTask=saved.path("createdTaskIds").get(0).asText();
        call(owner,get(base+"/plan-drafts/latest"),Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        call(owner,patch("/api/v1/tasks/"+newTask),Map.of("scheduledDate",today.plusDays(2).toString())).andExpect(status().isUnprocessableEntity());
        call(owner,patch("/api/v1/tasks/"+newTask),Map.of("scheduledDate",today.plusDays(4).toString())).andExpect(status().isOk());
        call(owner,post(base+"/plan-drafts"),Map.of()).andExpect(status().isCreated());
        assertTrue(jdbc.queryForObject("SELECT completed FROM study_tasks WHERE id=?",Boolean.class,UUID.fromString(done.path("id").asText())));
        assertEquals(today,jdbc.queryForObject("SELECT scheduled_date FROM study_tasks WHERE id=?",LocalDate.class,UUID.fromString(done.path("id").asText())));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(id)));
        call(foreign,get(base),Map.of()).andExpect(status().isNotFound());
        call(foreign,patch("/api/v1/tasks/"+newTask),Map.of("scheduledDate",today.plusDays(5).toString())).andExpect(status().isNotFound());
    }
    @Test void partialConfirmationRetainsEditedRemainderAndCanRescheduleWithoutCallingAi() throws Exception {
        var goal=create();String id=goal.path("id").asText(), base="/api/v1/goals/"+id;
        when(ai.generateProposalJson(any())).thenReturn("""
            {"tasks":[{"id":"a","topicId":"algebra","title":"Practice equations","estimatedMinutes":40},
            {"id":"b","topicId":"algebra","title":"Solve advanced problems","estimatedMinutes":100}]}
            """);
        var draft=data(call(owner,post(base+"/plan-drafts"),Map.of()).andExpect(status().isCreated()));
        assertEquals(1,draft.at("/proposal/unscheduled").size());
        var body=mapper.createObjectNode().put("expectedGoalVersion",draft.path("goalVersion").asInt()).put("allowPartial",true);
        var scheduled=(ObjectNode)draft.at("/proposal/tasks/0").deepCopy();scheduled.remove(List.of("priority","requiresContinuousSession"));
        body.putArray("tasks").add(scheduled);
        body.putArray("deferredTasks").addObject().put("id","b").put("topicId","algebra").put("title","Edited advanced problems").put("estimatedMinutes",110);
        String confirm=base+"/plan-drafts/"+draft.path("id").asText()+"/confirm",key=UUID.randomUUID().toString();
        var withoutDecision=body.deepCopy();withoutDecision.remove(List.of("allowPartial","deferredTasks"));
        call(owner,post(confirm).header("Idempotency-Key",UUID.randomUUID().toString()),withoutDecision).andExpect(status().isUnprocessableEntity());
        var forged=body.deepCopy();((ObjectNode)forged.path("deferredTasks").get(0)).put("topicId","foreign-topic");
        call(owner,post(confirm).header("Idempotency-Key",UUID.randomUUID().toString()),forged).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(id)));
        var saved=data(call(owner,post(confirm).header("Idempotency-Key",key),body).andExpect(status().isCreated()));
        assertEquals(1,saved.path("createdCount").asInt());assertEquals(1,saved.path("remainingCount").asInt());
        call(owner,post(confirm).header("Idempotency-Key",key),body).andExpect(status().isOk());
        var changed=body.deepCopy();changed.put("allowPartial",false);changed.remove("deferredTasks");
        call(owner,post(confirm).header("Idempotency-Key",key),changed).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(id)));
        String remaining=saved.path("remainingDraftId").asText();
        jdbc.update("UPDATE plan_drafts SET created_at=now()-interval '2 days', expires_at=now()-interval '1 day' WHERE id=?",UUID.fromString(remaining));
        var backlog=data(call(owner,get(base+"/plan-drafts/"+remaining),Map.of()).andExpect(status().isOk()));
        assertEquals(110,backlog.at("/proposal/unscheduled/0/estimatedMinutes").asInt());
        assertEquals("Edited advanced problems",backlog.at("/proposal/unscheduled/0/title").asText());
        assertEquals(1,data(call(owner,get(base+"/plan-drafts/pending"),Map.of()).andExpect(status().isOk())).size());
        jdbc.update("INSERT INTO plan_drafts(goal_id,goal_version,proposal,expires_at) SELECT goal_id,goal_version,proposal - 'backlogOnly',now()+interval '1 day' FROM plan_drafts WHERE id=?",UUID.fromString(remaining));
        assertEquals(1,data(call(owner,get(base+"/plan-drafts/pending"),Map.of()).andExpect(status().isOk())).size());
        call(foreign,get(base+"/plan-drafts/pending"),Map.of()).andExpect(status().isNotFound());
        call(foreign,post(base+"/plan-drafts/"+remaining+"/reschedule"),Map.of()).andExpect(status().isNotFound());
        var current=data(call(owner,get(base),Map.of()).andExpect(status().isOk()));
        call(owner,patch(base),Map.of("expectedVersion",current.path("version").asInt(),"minutesPerDay",120)).andExpect(status().isOk());
        var replanned=data(call(owner,post(base+"/plan-drafts/"+remaining+"/reschedule"),Map.of()).andExpect(status().isOk()));
        assertEquals(110,replanned.at("/proposal/tasks/0/estimatedMinutes").asInt());
        assertEquals(0,replanned.at("/proposal/unscheduled").size());
        var last=mapper.createObjectNode().put("expectedGoalVersion",replanned.path("goalVersion").asInt());
        var task=(ObjectNode)replanned.at("/proposal/tasks/0").deepCopy();task.remove(List.of("priority","requiresContinuousSession"));last.putArray("tasks").add(task);
        call(owner,post(base+"/plan-drafts/"+remaining+"/confirm").header("Idempotency-Key",UUID.randomUUID().toString()),last).andExpect(status().isCreated());
        assertEquals(0,data(call(owner,get(base+"/plan-drafts/pending"),Map.of()).andExpect(status().isOk())).size());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(id)));
        verify(ai,times(1)).generateProposalJson(any());
    }
    @Test void aiFailureKeepsConfiguredGoalAndDoesNotCreateTasks() throws Exception {
        var goal=create();String base="/api/v1/goals/"+goal.path("id").asText();
        when(ai.generateProposalJson(any())).thenThrow(new PlanException(503,"AI_UNAVAILABLE","Provider unavailable"));
        call(owner,post(base+"/plan-drafts"),Map.of()).andExpect(status().isServiceUnavailable());
        assertEquals(scope(),data(call(owner,get(base),Map.of()).andExpect(status().isOk())).get("planningScope"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(goal.path("id").asText())));
    }
    @Test void repairsInvalidProviderResultOnceAndReadsExpiredOrStaleDraftsWithoutAllowingConfirmation() throws Exception {
        var goal=create();String base="/api/v1/goals/"+goal.path("id").asText();
        when(ai.generateProposalJson(any())).thenReturn("{\"tasks\":[{\"id\":\"x\",\"topicId\":\"wrong-topic\",\"title\":\"Practice examples\",\"estimatedMinutes\":30}]}",proposal("Practice repaired algebra"));
        var draft=data(call(owner,post(base+"/plan-drafts"),Map.of()).andExpect(status().isCreated()));
        var requests=org.mockito.ArgumentCaptor.forClass(GeminiPlanService.GeneratePlanRequest.class);
        verify(ai,times(2)).generateProposalJson(requests.capture());
        assertEquals("UNKNOWN_TOPIC",requests.getAllValues().get(1).validationFeedback());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(goal.path("id").asText())));
        call(owner,post(base+"/tasks"),Map.of("title","New manual work","scheduledDate",today.toString(),"estimatedMinutes",10)).andExpect(status().isCreated());
        call(owner,get(base+"/plan-drafts/latest"),Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("STALE"));
        jdbc.update("UPDATE plan_drafts SET created_at=now()-interval '2 days',expires_at=now()-interval '1 day' WHERE id=?",UUID.fromString(draft.path("id").asText()));
        call(owner,get(base+"/plan-drafts/latest"),Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("EXPIRED"));
        var body=mapper.createObjectNode().put("expectedGoalVersion",draft.path("goalVersion").asInt());
        var item=(ObjectNode)draft.at("/proposal/tasks/0").deepCopy();item.remove(List.of("priority","requiresContinuousSession"));body.putArray("tasks").add(item);
        call(owner,post(base+"/plan-drafts/"+draft.path("id").asText()+"/confirm").header("Idempotency-Key",UUID.randomUUID().toString()),body).andExpect(status().isGone());
        call(foreign,get(base+"/plan-drafts/latest"),Map.of()).andExpect(status().isNotFound());
    }
    @Test void changingBudgetOrStartCannotInvalidateSavedCompletedWork() throws Exception {
        var goal=create();String base="/api/v1/goals/"+goal.path("id").asText();
        var task=data(call(owner,post(base+"/tasks"),Map.of("title","History","scheduledDate",today.toString(),"estimatedMinutes",30)).andExpect(status().isCreated()));
        call(owner,patch("/api/v1/tasks/"+task.path("id").asText()),Map.of("completed",true)).andExpect(status().isOk());
        int version=data(call(owner,get(base),Map.of()).andExpect(status().isOk())).path("version").asInt();
        var changed=scope();((ObjectNode)changed.get("dailyMinutes")).put(today.toString(),0);
        call(owner,patch(base),Map.of("expectedVersion",version,"planningScope",changed)).andExpect(status().isUnprocessableEntity());
        call(owner,patch(base),Map.of("expectedVersion",version,"startDate",today.plusDays(1).toString())).andExpect(status().isConflict());
        assertEquals(version,data(call(owner,get(base),Map.of()).andExpect(status().isOk())).path("version").asInt());
        assertTrue(jdbc.queryForObject("SELECT completed FROM study_tasks WHERE id=?",Boolean.class,UUID.fromString(task.path("id").asText())));
    }
}
