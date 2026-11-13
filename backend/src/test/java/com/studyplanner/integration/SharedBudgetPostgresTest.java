package com.studyplanner.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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

@SpringBootTest(properties={"spring.datasource.url=${planning.test-db-url:jdbc:postgresql://127.0.0.1:5432/study_planner_test}","spring.datasource.username=study_planner","spring.datasource.password=unused-local"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="planning.integration",matches="true")
class SharedBudgetPostgresTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper mapper;
    @MockBean GeminiPlanService ai;
    UUID owner,foreign;LocalDate today;
    @BeforeEach void seed() throws Exception {
        try(var connection=jdbc.getDataSource().getConnection()) {assertTrue(connection.getMetaData().getURL().endsWith("study_planner_test"));}
        owner=UUID.randomUUID();foreign=UUID.randomUUID();today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for(var id:List.of(owner,foreign))jdbc.update("INSERT INTO app_users(id,email,password_hash) VALUES(?,?,?)",id,id+"@example.test","unused");
    }
    @AfterEach void clean(){for(var id:List.of(owner,foreign))jdbc.update("DELETE FROM app_users WHERE id=?",id);}
    ResultActions call(UUID user,MockHttpServletRequestBuilder request,Object body)throws Exception {
        var p=new AppUserPrincipal(user,user+"@example.test","unused");
        return mvc.perform(request.with(authentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()))).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    JsonNode data(ResultActions result)throws Exception{return mapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data");}
    JsonNode budget(int minutes,int version,Map<String,Integer> overrides)throws Exception {
        return data(call(owner,put("/api/v1/study-budget"),Map.of("defaultMinutes",minutes,"expectedVersion",version,"dailyMinutes",overrides)).andExpect(status().isOk()));
    }
    String goal(String name)throws Exception {
        var scope=mapper.createObjectNode();scope.putArray("selectedTopics").addObject().put("id","t").put("title","Algebra");scope.putObject("dailyMinutes");
        return data(call(owner,post("/api/v1/goals"),Map.of("subject",name,"topics","Algebra","startDate",today.toString(),"examDate",today.plusDays(3).toString(),"minutesPerDay",90,"planningScope",scope))
            .andExpect(status().isCreated())).path("id").asText();
    }
    Map<String,Object> work(String title,LocalDate day,int minutes){return Map.of("title",title,"scheduledDate",day.toString(),"estimatedMinutes",minutes);}
    @Test void manualWorkCanExceedSubjectPreferenceButNeverSharedCapacity() throws Exception {
        budget(200,0,Map.of());String a=goal("A"),b=goal("B");
        var task=data(call(owner,post("/api/v1/goals/"+a+"/tasks"),work("Long manual study",today,180)).andExpect(status().isCreated()));
        call(owner,post("/api/v1/goals/"+b+"/tasks"),work("Over shared",today,21))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("SHARED_DAILY_BUDGET_EXCEEDED"));
        var g=data(call(owner,get("/api/v1/goals/"+a),Map.of()).andExpect(status().isOk()));
        call(owner,patch("/api/v1/goals/"+a),Map.of("expectedVersion",g.path("version").asInt(),"minutesPerDay",60)).andExpect(status().isOk());
        call(owner,patch("/api/v1/tasks/"+task.path("id").asText()),Map.of("scheduledDate",today.plusDays(1).toString())).andExpect(status().isOk());
        assertEquals(180,data(call(owner,get("/api/v1/study-budget"),Map.of()).andExpect(status().isOk())).at("/days/1/usedMinutes").asInt());
    }
    @Test void preferenceExcessRequiresExplicitConsentAndReplayKeepsThatDecision() throws Exception {
        budget(200,0,Map.of(today.plusDays(1).toString(),0,today.plusDays(2).toString(),0));String g=goal("Math");
        when(ai.generateProposalJson(any())).thenReturn("{\"tasks\":[{\"id\":\"one\",\"topicId\":\"t\",\"title\":\"Solve equations\",\"estimatedMinutes\":180}]}");
        String base="/api/v1/goals/"+g+"/plan-drafts";
        var draft=data(call(owner,post(base),Map.of()).andExpect(status().isCreated()));
        assertEquals(90,draft.at("/proposal/summary/preferenceExcessDays/0/excessMinutes").asInt());
        assertEquals(200,draft.at("/proposal/summary/availableMinutes").asInt());
        var body=confirmation(draft,false);String url=base+"/"+draft.path("id").asText()+"/confirm",key=UUID.randomUUID().toString();
        call(owner,post(url).header("Idempotency-Key",key),body).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("PREFERENCE_EXCEEDANCE_REQUIRES_CONFIRMATION"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(g)));
        body.put("allowPreferenceExceedance",true);
        call(foreign,post(url).header("Idempotency-Key",key),body).andExpect(status().isNotFound());
        call(owner,post(url).header("Idempotency-Key",key),body).andExpect(status().isCreated());
        call(owner,post(url).header("Idempotency-Key",key),body).andExpect(status().isOk());
        body.put("allowPreferenceExceedance",false);
        call(owner,post(url).header("Idempotency-Key",key),body).andExpect(status().isConflict());
        assertEquals(90,jdbc.queryForObject("SELECT minutes_per_day FROM study_goals WHERE id=?",Integer.class,UUID.fromString(g)));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(g)));
    }
    @Test void decliningExcessMovesWorkToAnotherDayWithoutCallingAiAgain() throws Exception {
        budget(200,0,Map.of());String g=goal("Math");
        when(ai.generateProposalJson(any())).thenReturn(mapper.writeValueAsString(Map.of("tasks",List.of(
            Map.of("id","one","topicId","t","title","Solve equations","estimatedMinutes",90,"scheduledDate",today.toString()),
            Map.of("id","two","topicId","t","title","Review solutions","estimatedMinutes",60,"scheduledDate",today.toString())))));
        String base="/api/v1/goals/"+g+"/plan-drafts";
        var draft=data(call(owner,post(base),Map.of()).andExpect(status().isCreated()));
        assertEquals(60,draft.at("/proposal/summary/preferenceExcessDays/0/excessMinutes").asInt());
        var strict=data(call(owner,post(base+"/"+draft.path("id").asText()+"/reschedule"),Map.of("respectPreferences",true)).andExpect(status().isOk()));
        assertTrue(strict.at("/proposal/summary/preferenceExcessDays").isEmpty());
        assertEquals(today.plusDays(1).toString(),strict.at("/proposal/tasks/1/scheduledDate").asText());
        assertEquals(60,strict.at("/proposal/tasks/1/estimatedMinutes").asInt());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(g)));
        verify(ai,times(1)).generateProposalJson(any());
    }
    @Test void strictPreferenceKeepsUnfittableWorkAndConsentNeverBypassesSharedBudget() throws Exception {
        budget(200,0,Map.of());String g=goal("Math"),other=goal("Other");
        when(ai.generateProposalJson(any())).thenReturn("{\"tasks\":[{\"id\":\"one\",\"topicId\":\"t\",\"title\":\"Solve equations\",\"estimatedMinutes\":180}]}");
        String base="/api/v1/goals/"+g+"/plan-drafts";
        var draft=data(call(owner,post(base),Map.of()).andExpect(status().isCreated()));
        call(owner,post("/api/v1/goals/"+other+"/tasks"),work("New reservation",today,40)).andExpect(status().isCreated());
        var body=confirmation(draft,false).put("allowPreferenceExceedance",true);
        call(owner,post(base+"/"+draft.path("id").asText()+"/confirm").header("Idempotency-Key",UUID.randomUUID().toString()),body)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("SHARED_DAILY_BUDGET_EXCEEDED"));
        var strict=data(call(owner,post(base+"/"+draft.path("id").asText()+"/reschedule"),Map.of("respectPreferences",true)).andExpect(status().isOk()));
        assertEquals(0,strict.at("/proposal/tasks").size());assertEquals(180,strict.at("/proposal/unscheduled/0/estimatedMinutes").asInt());
        assertTrue(strict.at("/proposal/scope/respectPreferences").asBoolean());
        verify(ai,times(1)).generateProposalJson(any());
    }
    @Test void missingSetupIsExplicitAndAuthCsrfAndValidationAreEnforced()throws Exception {
        String goal=goal("A");
        assertTrue(data(call(owner,get("/api/v1/study-budget"),Map.of()).andExpect(status().isOk())).path("defaultMinutes").isNull());
        call(owner,post("/api/v1/goals/"+goal+"/tasks"),work("Work",today,30)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("STUDY_BUDGET_REQUIRED"));
        call(owner,post("/api/v1/goals/"+goal+"/plan-drafts"),Map.of()).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(ai);
        mvc.perform(get("/api/v1/study-budget")).andExpect(status().isUnauthorized());
        var p=new AppUserPrincipal(owner,"x@example.test","unused");
        mvc.perform(put("/api/v1/study-budget").with(authentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()))).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        call(owner,put("/api/v1/study-budget"),Map.of("defaultMinutes",-1,"expectedVersion",0,"dailyMinutes",Map.of())).andExpect(status().isBadRequest());
        call(owner,put("/api/v1/study-budget"),Map.of("defaultMinutes",120,"expectedVersion",0,"dailyMinutes",Map.of("bad-date",0))).andExpect(status().isBadRequest());
        call(owner,get("/api/v1/study-budget?from="+today+"&to="+today.plusDays(90)),Map.of()).andExpect(status().isBadRequest());
    }
    @Test void totalsIncludeCompletedAcrossGoalsAndMovingReleasesCapacityWithoutTouchingOtherOwners()throws Exception {
        budget(120,0,Map.of(today.plusDays(1).toString(),0));String a=goal("A"),b=goal("B");
        var first=data(call(owner,post("/api/v1/goals/"+a+"/tasks"),work("A work",today,90)).andExpect(status().isCreated()));
        call(owner,patch("/api/v1/tasks/"+first.path("id").asText()),Map.of("completed",true)).andExpect(status().isOk());
        call(owner,post("/api/v1/goals/"+b+"/tasks"),work("Too much",today,31)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("SHARED_DAILY_BUDGET_EXCEEDED"));
        var second=data(call(owner,post("/api/v1/goals/"+b+"/tasks"),work("B work",today,30)).andExpect(status().isCreated()));
        var summary=data(call(owner,get("/api/v1/study-budget"),Map.of()).andExpect(status().isOk()));
        assertEquals(120,summary.at("/days/0/usedMinutes").asInt());assertEquals(0,summary.at("/days/0/remainingMinutes").asInt());
        assertEquals(0,data(call(foreign,get("/api/v1/study-budget"),Map.of()).andExpect(status().isOk())).at("/days/0/usedMinutes").asInt());
        String task="/api/v1/tasks/"+second.path("id").asText();
        call(owner,patch(task),Map.of("scheduledDate",today.plusDays(1).toString())).andExpect(status().isUnprocessableEntity());
        call(owner,patch(task),Map.of("scheduledDate",today.plusDays(2).toString())).andExpect(status().isOk());
        assertEquals(90,data(call(owner,get("/api/v1/study-budget"),Map.of()).andExpect(status().isOk())).at("/days/0/usedMinutes").asInt());
        call(owner,put("/api/v1/study-budget"),Map.of("defaultMinutes",80,"expectedVersion",1,"dailyMinutes",Map.of())).andExpect(status().isUnprocessableEntity());
        var updated=budget(60,1,Map.of(today.toString(),90,today.plusDays(1).toString(),0));
        assertEquals(2,updated.path("version").asInt());assertEquals(0,updated.at("/dailyMinutes/"+today.plusDays(1)).asInt(-1));
        call(owner,put("/api/v1/study-budget"),Map.of("defaultMinutes",120,"expectedVersion",1,"dailyMinutes",Map.of())).andExpect(status().isConflict());
        call(foreign,patch(task),Map.of("scheduledDate",today.toString())).andExpect(status().isNotFound());
        call(owner,patch("/api/v1/tasks/"+first.path("id").asText()),Map.of("completed",false)).andExpect(status().isOk());
    }
    @Test void aiAllocatesAgainstOtherGoalsKeepsUnscheduledAndConfirmRechecksCurrentSharedBudget()throws Exception {
        budget(120,0,Map.of(today.plusDays(1).toString(),0));String a=goal("A"),b=goal("B");
        call(owner,post("/api/v1/goals/"+a+"/tasks"),work("A consumes today",today,90)).andExpect(status().isCreated());
        when(ai.generateProposalJson(any())).thenReturn("""
            {"tasks":[{"id":"one","topicId":"t","title":"Practice equations","estimatedMinutes":90},
            {"id":"two","topicId":"t","title":"Review mistakes","estimatedMinutes":90}]}
            """);
        String base="/api/v1/goals/"+b+"/plan-drafts";
        var draft=data(call(owner,post(base),Map.of()).andExpect(status().isCreated()));
        assertEquals(today.plusDays(2).toString(),draft.at("/proposal/tasks/0/scheduledDate").asText());
        assertEquals(90,draft.at("/proposal/unscheduled/0/estimatedMinutes").asInt());
        assertEquals(30,draft.at("/proposal/summary/shortfallMinutes").asInt());
        assertTrue(draft.at("/proposal/scope/sharedBudgetLimitedDays").asInt()>0);
        var body=confirmation(draft,true);
        // Another goal changes after generation without advancing this goal's version.
        call(owner,post("/api/v1/goals/"+a+"/tasks"),work("New reservation",today.plusDays(2),40)).andExpect(status().isCreated());
        call(owner,post(base+"/"+draft.path("id").asText()+"/confirm").header("Idempotency-Key",UUID.randomUUID().toString()),body)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("SHARED_DAILY_BUDGET_EXCEEDED"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE goal_id=?",Integer.class,UUID.fromString(b)));
        var replanned=data(call(owner,post(base+"/"+draft.path("id").asText()+"/reschedule"),Map.of()).andExpect(status().isOk()));
        assertEquals(0,replanned.at("/proposal/tasks").size());assertEquals(2,replanned.at("/proposal/unscheduled").size());
        verify(ai,times(1)).generateProposalJson(any());
    }
    ObjectNode confirmation(JsonNode draft,boolean partial) {
        var body=mapper.createObjectNode().put("expectedGoalVersion",draft.path("goalVersion").asInt());
        var list=body.putArray("tasks");
        for(var item:draft.at("/proposal/tasks")){var copy=(ObjectNode)item.deepCopy();copy.remove(List.of("priority","requiresContinuousSession"));list.add(copy);}
        if(partial)body.put("allowPartial",true);
        return body;
    }
    @Test void concurrentManualAndAiConfirmCannotReserveTwoNinetyMinuteGoalsIn120()throws Exception {
        budget(120,0,Map.of());String a=goal("A"),b=goal("B");
        when(ai.generateProposalJson(any())).thenReturn("{\"tasks\":[{\"id\":\"one\",\"topicId\":\"t\",\"title\":\"Practice equations\",\"estimatedMinutes\":90}]}");
        var draft=data(call(owner,post("/api/v1/goals/"+b+"/plan-drafts"),Map.of()).andExpect(status().isCreated()));
        var gate=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            var manual=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return call(owner,post("/api/v1/goals/"+a+"/tasks"),work("Manual reservation",today,90)).andReturn().getResponse().getStatus();});
            var confirm=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return call(owner,post("/api/v1/goals/"+b+"/plan-drafts/"+draft.path("id").asText()+"/confirm").header("Idempotency-Key",UUID.randomUUID().toString()),confirmation(draft,false)).andReturn().getResponse().getStatus();});
            assertEquals(Set.of(201,422),Set.of(manual.get(10,TimeUnit.SECONDS),confirm.get(10,TimeUnit.SECONDS)));
            assertEquals(90,data(call(owner,get("/api/v1/study-budget"),Map.of()).andExpect(status().isOk())).at("/days/0/usedMinutes").asInt());
        }finally{pool.shutdownNow();}
    }
    @Test void loweringBudgetAndCreatingWorkConcurrentlyCannotCommitAnOverfullDay()throws Exception {
        budget(120,0,Map.of());String a=goal("A");var gate=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            var save=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return call(owner,put("/api/v1/study-budget"),Map.of("defaultMinutes",60,"expectedVersion",1,"dailyMinutes",Map.of())).andReturn().getResponse().getStatus();});
            var create=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return call(owner,post("/api/v1/goals/"+a+"/tasks"),work("New work",today,90)).andReturn().getResponse().getStatus();});
            int saveStatus=save.get(10,TimeUnit.SECONDS),createStatus=create.get(10,TimeUnit.SECONDS);
            assertTrue((saveStatus==200&&createStatus==422)||(saveStatus==422&&createStatus==201));
            var day=data(call(owner,get("/api/v1/study-budget"),Map.of()).andExpect(status().isOk())).at("/days/0");
            assertTrue(day.path("usedMinutes").asInt()<=day.path("budgetMinutes").asInt());
        }finally{pool.shutdownNow();}
    }
}
