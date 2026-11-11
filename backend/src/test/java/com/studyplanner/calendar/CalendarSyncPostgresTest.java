package com.studyplanner.calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.*;
import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.goals.*;
import com.studyplanner.tasks.*;
import static com.studyplanner.calendar.CalendarScheduling.*;

@SpringBootTest(properties={"spring.datasource.url=${calendar.test-db-url:jdbc:postgresql://127.0.0.1:5432/study_planner_test}","spring.datasource.username=study_planner","spring.datasource.password=unused-local"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="calendar.integration",matches="true")
class CalendarSyncPostgresTest {
    @Autowired JdbcTemplate jdbc;@Autowired CalendarSyncStore queue;@Autowired CalendarStore exports;@Autowired TaskService tasks;@Autowired GoalService goals;
    @Autowired ObjectMapper mapper;@Autowired PlatformTransactionManager transactions;@Autowired ApplicationContext context;@Autowired MockMvc mvc;
    @MockBean GoogleCalendarClient google;
    UUID owner,foreign,goal,task;LocalDate today;Instant start;
    @BeforeEach void seed()throws Exception {
        try(var db=jdbc.getDataSource().getConnection()){assertTrue(db.getMetaData().getURL().endsWith("study_planner_test"));}
        assertTrue(context.getBeansOfType(CalendarSyncWorker.class).isEmpty());
        owner=UUID.randomUUID();foreign=UUID.randomUUID();goal=UUID.randomUUID();task=UUID.randomUUID();today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));start=today.plusDays(1).atTime(9,0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        for(var user:List.of(owner,foreign))jdbc.update("INSERT INTO app_users(id,email,password_hash,study_daily_minutes) VALUES(?,?,?,240)",user,user+"@example.test","unused");
        jdbc.update("INSERT INTO study_goals(id,user_id,subject,topics,start_date,exam_date,minutes_per_day) VALUES(?,?,'Math','Algebra',?,?,120)",goal,owner,today,today.plusDays(6));
        jdbc.update("INSERT INTO study_tasks(id,goal_id,title,scheduled_date,estimated_minutes) VALUES(?,?,'Original',?,60)",task,goal,today.plusDays(1));
        var snapshot=new Task(task,goal,"Original","Math",today.plusDays(1),60,false,today.plusDays(6));
        exports.begin(owner,task,"target","event-"+task,start,start.plusSeconds(3600),mapper.writeValueAsString(snapshot));exports.confirmed(owner,task,"https://calendar.google.com/example");
    }
    @AfterEach void clean(){for(var user:List.of(owner,foreign))jdbc.update("DELETE FROM app_users WHERE id=?",user);}
    int jobCount(){return jdbc.queryForObject("SELECT count(*) FROM calendar_sync_jobs WHERE user_id=?",Integer.class,owner);}
    @Test void taskEditsEnqueueAtomicallyCompletionDoesNotAndGoalDeletionRetainsDeleteTombstone() {
        tasks.setCompleted(owner,goal,task,true);assertEquals(0,jobCount());
        tasks.updateTask(owner,goal,task,new TaskService.TaskPatch("Renamed",null,null,null));
        assertEquals(1,jobCount());assertEquals("UPDATE",queue.summary(owner).items().getFirst().operation());
        assertThrows(GoalService.GoalNotFoundException.class,()->tasks.updateTask(foreign,goal,task,new TaskService.TaskPatch("Hacked",null,null,null)));
        goals.deleteGoal(owner,goal);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE id=?",Integer.class,task));
        assertEquals("DELETE",queue.summary(owner).items().getFirst().operation());
        var job=queue.claim().orElseThrow();assertEquals(owner,job.owner());assertEquals(task,job.taskId());
        assertTrue(queue.finish(job,"DELETED","Deleted",null,null,null));
        assertTrue(exports.attempts(owner).isEmpty());assertEquals(1,queue.summary(owner).deleted());
        assertTrue(queue.claim().isEmpty());
    }
    @Test void failedLocalTransactionNeverLeavesSyncIntent() {
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            tasks.updateTask(owner,goal,task,new TaskService.TaskPatch("Rolled back",null,null,null));throw new IllegalStateException("rollback");
        }));
        assertEquals(0,jobCount());assertEquals("Original",tasks.getTask(owner,goal,task).title());
    }
    @Test void ownerSerializationAllowsIndependentExportReceiptForeignKeyCheck() {
        UUID anotherTask=UUID.randomUUID();
        assertTimeoutPreemptively(Duration.ofSeconds(5),()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            exports.lockOwner(owner);
            // The REQUIRES_NEW receipt must not wait on our own outer owner lock.
            exports.begin(owner,anotherTask,"target","independent-event",start,start.plusSeconds(3600),"{}");
        }));
        assertEquals(2,exports.attempts(owner).size());
    }
    @Test void laterDeleteSurvivesOldWorkerReceiptAndLeasePreventsConcurrentClaim() {
        tasks.updateTask(owner,goal,task,new TaskService.TaskPatch("First edit",null,null,null));var first=queue.claim().orElseThrow();
        assertTrue(queue.claim().isEmpty());
        tasks.deleteTask(owner,goal,task);
        assertFalse(queue.current(first));
        assertFalse(queue.finish(first,"IN_SYNC","Old result",new Slot(new Task(task,goal,"First edit","Math",today.plusDays(1),60,false,today.plusDays(6)),start,start.plusSeconds(3600),null),"{}",""));
        var next=queue.claim().orElseThrow();assertEquals("DELETE",next.operation());assertTrue(next.revision()>first.revision());
        queue.finish(next,"RETRY","Offline",null,null,null);assertEquals(1,queue.summary(owner).pending());
        assertTrue(queue.claim().isEmpty());queue.retry(owner);assertTrue(queue.claim().isPresent());
    }
    @Test void subjectRenameEnqueuesExistingEventsAndOwnerDeletionCascadesWithoutTriggerFailure() {
        var goalData=goals.getGoal(owner,goal);
        goals.patchGoal(owner,goal,new GoalPatch("New subject",null,null,null,null,null),goalData.version());
        assertEquals(1,jobCount());
        jdbc.update("DELETE FROM app_users WHERE id=?",owner);
        assertEquals(0,jobCount());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM calendar_exports WHERE user_id=?",Integer.class,owner));
    }
    @Test void syncEndpointsRequireSessionCsrfAndOnlyExposeOwnJobs()throws Exception {
        tasks.updateTask(owner,goal,task,new TaskService.TaskPatch("Renamed",null,null,null));
        mvc.perform(get("/api/v1/calendar/sync")).andExpect(status().isUnauthorized());
        var user=new AppUserPrincipal(foreign,"foreign@example.test","unused");
        var auth=authentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
        mvc.perform(get("/api/v1/calendar/sync").with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.pending").value(0));
        mvc.perform(post("/api/v1/calendar/sync/retry").with(auth).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/calendar/sync/retry").with(auth).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        verifyNoInteractions(google);
    }
}
