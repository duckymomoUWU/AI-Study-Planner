package com.studyplanner.calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.studyplanner.calendar.CalendarScheduling.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyplanner.goals.PlanException;
import java.time.*;
import java.util.*;

class CalendarSyncServiceTest {
    CalendarSyncStore queue=mock(CalendarSyncStore.class);CalendarStore exports=mock(CalendarStore.class);GoogleCalendarClient google=mock(GoogleCalendarClient.class);
    ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    UUID owner=UUID.randomUUID(),taskId=UUID.randomUUID(),goal=UUID.randomUUID();
    Instant now=Instant.parse("2026-11-11T00:00:00Z"),start=Instant.parse("2026-11-11T02:00:00Z");LocalDate date=LocalDate.of(2026,11,11);
    CalendarSyncStore.Job job;CalendarSyncService service;Task original;
    @BeforeEach void setup()throws Exception {
        job=new CalendarSyncStore.Job(owner,taskId,"target","event","Work","UPDATE",1,1,UUID.randomUUID(),now.plusSeconds(300));
        original=new Task(taskId,goal,"Work","Math",date,60,false,date.plusDays(10));
        service=new CalendarSyncService(queue,exports,google,mapper,Clock.fixed(now,ZoneOffset.UTC));
        when(queue.claim()).thenReturn(Optional.of(job));when(queue.current(any())).thenReturn(true);
        when(google.connected(owner)).thenReturn(true);when(google.event(owner,"target","event")).thenAnswer(i->Optional.of(event()));
        when(exports.attempts(owner)).thenReturn(List.of(new CalendarStore.Attempt(taskId,"target","event",start,start.plusSeconds(3600),mapper.writeValueAsString(original),"CONFIRMED","https://calendar.google.com/example")));
        when(queue.task(owner,taskId)).thenReturn(Optional.of(original));
        var s=defaults();when(exports.settings(owner)).thenReturn(Optional.of(mapper.writeValueAsString(new Settings(s.timezone(),s.days(),s.blocks(),10,List.of("busy"),"target"))));
        when(google.calendars(owner)).thenReturn(List.of(new GoogleCalendarClient.CalendarInfo("target","Study",true,true),new GoogleCalendarClient.CalendarInfo("busy","Work",false,false)));
        when(google.busyExceptEvent(any(),anyString(),anyString(),any(),any(),anyString())).thenReturn(List.of());
        when(google.busy(any(),anyList(),any(),any(),anyString())).thenReturn(List.of());
        when(queue.reservations(any(),any(),any(),any())).thenReturn(List.of());
        when(google.patch(eq(owner),eq("target"),eq("event"),any(),anyString())).thenAnswer(i->{var e=event();var body=(ObjectNode)i.getArgument(3);body.fields().forEachRemaining(f->e.set(f.getKey(),f.getValue()));return e;});
    }
    ObjectNode event(){var e=mapper.createObjectNode().put("id","event").put("etag","version-1").put("htmlLink","https://calendar.google.com/example");
        e.putObject("start").put("dateTime",start.toString());e.putObject("end").put("dateTime",start.plusSeconds(3600).toString());
        e.putObject("extendedProperties").putObject("private").put("studyPlannerOwner",owner.toString()).put("studyPlannerTask",taskId.toString());return e;}
    void deleting(){job=new CalendarSyncStore.Job(owner,taskId,"target","event","Work","DELETE",1,1,job.token(),job.leaseUntil());when(queue.claim()).thenReturn(Optional.of(job));}
    @Test void renameUpdatesSameEventWithoutChangingItsTimeOrCreatingAnother() {
        when(queue.task(owner,taskId)).thenReturn(Optional.of(new Task(taskId,goal,"Renamed","Math",date,60,false,date.plusDays(10))));
        assertTrue(service.processNext());var body=org.mockito.ArgumentCaptor.forClass(ObjectNode.class);
        verify(google).patch(eq(owner),eq("target"),eq("event"),body.capture(),eq("version-1"));assertEquals("Math · Renamed",body.getValue().path("summary").asText());assertFalse(body.getValue().has("start"));
        verify(queue).finish(eq(job),eq("IN_SYNC"),anyString(),any(),anyString(),anyString());verify(google,never()).insert(any(),anyString(),any());
    }
    @Test void movingCompletedWorkKeepsOriginalHourWhenFreeAndChecksBusyWithoutItself() {
        when(queue.task(owner,taskId)).thenReturn(Optional.of(new Task(taskId,goal,"Work","Math",date.plusDays(1),90,true,date.plusDays(10))));
        service.processNext();var body=org.mockito.ArgumentCaptor.forClass(ObjectNode.class);
        verify(google).patch(eq(owner),eq("target"),eq("event"),body.capture(),eq("version-1"));
        assertEquals(start.plusSeconds(86400).toString(),body.getValue().at("/start/dateTime").asText());
        assertEquals(start.plusSeconds(86400+5400).toString(),body.getValue().at("/end/dateTime").asText());
        verify(google,atLeast(2)).busyExceptEvent(eq(owner),eq("target"),eq("event"),any(),any(),anyString());
    }
    @Test void noSlotKeepsOldEventAndBlocksUntilUserAdjusts() {
        when(queue.task(owner,taskId)).thenReturn(Optional.of(new Task(taskId,goal,"Work","Math",date.plusDays(1),90,false,date.plusDays(10))));
        when(google.busy(any(),anyList(),any(),any(),anyString())).thenReturn(List.of(new Busy(now,now.plusSeconds(86400*3))));
        service.processNext();verify(google,never()).patch(any(),anyString(),anyString(),any(),anyString());verify(google,never()).delete(any(),anyString(),anyString(),anyString());
        verify(queue).finish(eq(job),eq("BLOCKED"),contains("khung giờ"),isNull(),isNull(),isNull());
    }
    @Test void staleRemoteTimingFromAnEarlierLostResponseIsReconciledEvenIfAppMovedBack() {
        var remote=event();((ObjectNode)remote.get("start")).put("dateTime",start.plusSeconds(86400).toString());((ObjectNode)remote.get("end")).put("dateTime",start.plusSeconds(90000).toString());
        when(google.event(owner,"target","event")).thenReturn(Optional.of(remote));
        service.processNext();var body=org.mockito.ArgumentCaptor.forClass(ObjectNode.class);verify(google).patch(eq(owner),eq("target"),eq("event"),body.capture(),anyString());
        assertEquals(start.toString(),body.getValue().at("/start/dateTime").asText());
    }
    @Test void deleteUsesMarkersAndEtagAndTreatsAlreadyMissingAsSuccess() {
        deleting();service.processNext();verify(google).delete(owner,"target","event","version-1");
        verify(queue).finish(eq(job),eq("DELETED"),anyString(),isNull(),isNull(),isNull());
        clearInvocations(google,queue);when(google.event(owner,"target","event")).thenReturn(Optional.empty());service.processNext();
        verify(google,never()).delete(any(),anyString(),anyString(),anyString());verify(queue).finish(eq(job),eq("DELETED"),anyString(),isNull(),isNull(),isNull());
    }
    @Test void foreignMarkersNeverUpdateOrDeleteAnything() {
        deleting();var e=event();((ObjectNode)e.at("/extendedProperties/private")).put("studyPlannerOwner",UUID.randomUUID().toString());when(google.event(owner,"target","event")).thenReturn(Optional.of(e));
        service.processNext();verify(google,never()).delete(any(),anyString(),anyString(),anyString());verify(queue).finish(eq(job),eq("BLOCKED"),anyString(),isNull(),isNull(),isNull());
    }
    @Test void failuresAndDisconnectRetainDurableRetryAndNewerEditsPreventOldWrites() {
        when(google.connected(owner)).thenReturn(false);service.processNext();verify(queue).finish(eq(job),eq("RETRY"),contains("kết nối lại"),isNull(),isNull(),isNull());
        when(google.connected(owner)).thenReturn(true);when(queue.current(job)).thenReturn(false);clearInvocations(google);service.processNext();
        verify(google,never()).patch(any(),anyString(),anyString(),any(),anyString());
    }
    @Test void etagConflictRetriesWithoutCreatingDuplicates() {
        when(google.patch(any(),anyString(),anyString(),any(),anyString())).thenThrow(new PlanException(409,"CALENDAR_REMOTE_CHANGED","Changed"));
        service.processNext();verify(queue).finish(eq(job),eq("RETRY"),anyString(),isNull(),isNull(),isNull());verify(google,never()).insert(any(),anyString(),any());
    }
    @Test void lostRemoteEventIsNotSilentlyRecreated() {
        when(google.event(owner,"target","event")).thenReturn(Optional.empty());service.processNext();verify(queue).finish(eq(job),eq("BLOCKED"),contains("bị xóa"),isNull(),isNull(),isNull());verify(google,never()).insert(any(),anyString(),any());
    }
    @Test void overlappingOtherTargetEventsAndAllDayEventsRemainBusy()throws Exception {
        var data=mapper.readTree("""
            {"timeZone":"Asia/Ho_Chi_Minh","items":[
            {"id":"self","start":{"dateTime":"2026-11-11T02:00:00Z"},"end":{"dateTime":"2026-11-11T03:00:00Z"}},
            {"id":"other","start":{"dateTime":"2026-11-11T02:15:00Z"},"end":{"dateTime":"2026-11-11T02:45:00Z"}},
            {"id":"all-day","start":{"date":"2026-11-12"},"end":{"date":"2026-11-13"}},
            {"id":"free","transparency":"transparent"}]}
            """);
        var busy=GoogleCalendarClient.eventBusy(data,"self","UTC");assertEquals(2,busy.size());assertTrue(busy.getFirst().overlaps(start,start.plusSeconds(3600)));
        assertEquals(Instant.parse("2026-11-11T17:00:00Z"),busy.get(1).start());
    }
}
