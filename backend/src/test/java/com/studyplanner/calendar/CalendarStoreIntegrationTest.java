package com.studyplanner.calendar;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.goals.PlanException;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(properties={"spring.datasource.url=${calendar.test-db-url:jdbc:postgresql://127.0.0.1:5432/study_planner_test}","spring.datasource.username=study_planner","spring.datasource.password=unused-local"})
@EnabledIfSystemProperty(named="calendar.integration",matches="true")
class CalendarStoreIntegrationTest {
    @Autowired CalendarStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CalendarService service;
    @Autowired ObjectMapper mapper;
    @MockBean GoogleCalendarClient google;
    UUID owner,foreign;
    @BeforeEach void seed() {
        owner=UUID.randomUUID();foreign=UUID.randomUUID();
        for(UUID id:List.of(owner,foreign)) jdbc.update("INSERT INTO app_users(id,email,password_hash) VALUES (?,?,?)",id,"calendar-"+id+"@example.test","unused");
    }
    @AfterEach void clean() { for(UUID id:List.of(owner,foreign)) jdbc.update("DELETE FROM app_users WHERE id=?",id); }
    @Test void previewIsOwnedAndExpiredPreviewCannotBeRead() {
        UUID id=UUID.randomUUID();store.preview(owner,id,"{}",Instant.now().plusSeconds(600));
        assertTrue(store.preview(owner,id).isPresent());assertTrue(store.preview(foreign,id).isEmpty());
        jdbc.update("UPDATE calendar_previews SET expires_at=now()-interval '1 minute' WHERE id=?",id);assertTrue(store.preview(owner,id).isEmpty());
    }
    @Test void receiptsSurviveOuterRollbackAndDuplicateAttemptsKeepOriginalSlot() {
        var task=UUID.randomUUID();var start=Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var end=start.plusSeconds(3600);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            store.lock(owner);
            store.begin(owner,task,"calendar","event",start,end,"snapshot");
            store.confirmed(owner,task,"https://calendar.google.com/example");
            throw new IllegalStateException("Simulated later batch failure");
        }));
        assertTrue(store.exported(owner).contains(task));assertTrue(store.exported(foreign).isEmpty());
        store.begin(owner,task,"different-calendar","event2",end,end.plusSeconds(3600),"changed");
        var attempts=store.attempts(owner);assertEquals(1,attempts.size());assertEquals("calendar",attempts.getFirst().calendarId());assertEquals(start,attempts.getFirst().start());
    }
    @Test void settingsRemainPrivateToTheirOwner() {
        store.settings(owner,"private-settings");assertEquals("private-settings",store.settings(owner).orElseThrow());assertTrue(store.settings(foreign).isEmpty());
    }
    CalendarService.Preview plan(Map<String,JsonNode> remote, AtomicInteger inserts, boolean timeout) {
        LocalDate day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1); UUID goal=UUID.randomUUID(),task=UUID.randomUUID();
        jdbc.update("INSERT INTO study_goals(id,user_id,subject,topics,start_date,exam_date,minutes_per_day) VALUES (?,?,?,?,?,?,?)",goal,owner,"Math","Algebra",day,day.plusDays(10),120);
        jdbc.update("INSERT INTO study_tasks(id,goal_id,title,scheduled_date,estimated_minutes) VALUES (?,?,?,?,?)",task,goal,"Study algebra",day,60);
        var base=CalendarScheduling.defaults();
        service.settings(owner,new CalendarScheduling.Settings(base.timezone(),base.days(),base.blocks(),10,List.of("busy"),"target"));
        when(google.calendars(owner)).thenReturn(List.of(new GoogleCalendarClient.CalendarInfo("target","Study",true,true),new GoogleCalendarClient.CalendarInfo("busy","Work",false,false)));
        when(google.busy(eq(owner),anyList(),any(),any(),anyString())).thenReturn(List.of());
        when(google.event(eq(owner),anyString(),anyString())).thenAnswer(call->Optional.ofNullable(remote.get(call.getArgument(2,String.class))));
        when(google.insert(eq(owner),anyString(),any())).thenAnswer(call->{
            inserts.incrementAndGet();var event=((JsonNode)call.getArgument(2)).deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode)event).put("htmlLink","https://calendar.google.com/example");remote.put(event.path("id").asText(),event);
            if(timeout) throw new PlanException(502,"CALENDAR_UNAVAILABLE","Simulated lost response");
            return event;
        });
        return service.preview(owner,day,null);
    }
    @Test void databaseOutboxRecoversRemoteSuccessAfterTimeoutWithoutDuplicate() {
        var remote=new ConcurrentHashMap<String,JsonNode>();var inserts=new AtomicInteger();var preview=plan(remote,inserts,true);
        assertEquals(1,service.confirm(owner,preview.id()).failed());
        assertEquals("PENDING",store.attempts(owner).getFirst().status());
        assertEquals(1,service.confirm(owner,preview.id()).confirmed());
        assertEquals("CONFIRMED",store.attempts(owner).getFirst().status());
        assertEquals(1,inserts.get());assertEquals(1,remote.size());
    }
    @Test void simultaneousConfirmationsInsertOnlyOneRemoteEvent() throws Exception {
        var remote=new ConcurrentHashMap<String,JsonNode>();var inserts=new AtomicInteger();var preview=plan(remote,inserts,false);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var one=executor.submit(()->service.confirm(owner,preview.id()));var two=executor.submit(()->service.confirm(owner,preview.id()));
            assertEquals(1,one.get(15,TimeUnit.SECONDS).confirmed());assertEquals(1,two.get(15,TimeUnit.SECONDS).confirmed());
            assertEquals(1,inserts.get());assertEquals(1,store.attempts(owner).size());
        } finally { executor.shutdownNow(); }
    }
}
