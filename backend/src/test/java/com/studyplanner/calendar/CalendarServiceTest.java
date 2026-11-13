package com.studyplanner.calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.studyplanner.calendar.CalendarScheduling.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.*;
import com.studyplanner.goals.PlanException;
import java.time.*;
import java.util.*;

class CalendarServiceTest {
    CalendarStore store=mock(CalendarStore.class); GoogleCalendarClient google=mock(GoogleCalendarClient.class);
    ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    UUID owner=UUID.randomUUID(),previewId=UUID.randomUUID(); LocalDate date=LocalDate.of(2026,11,11);
    Instant now=Instant.parse("2026-11-11T00:00:00Z"),start=Instant.parse("2026-11-11T02:00:00Z");
    Settings settings; Task task; CalendarService service; CalendarService.Preview preview;
    @BeforeEach void setup() throws Exception {
        var base=defaults();settings=new Settings(base.timezone(),base.days(),base.blocks(),10,List.of("busy"),"target");
        task=new Task(UUID.randomUUID(),UUID.randomUUID(),"Learn algebra","Math",date,60,false,date.plusDays(10));
        preview=new CalendarService.Preview(previewId,date,date.plusDays(6),settings,List.of(new Slot(task,start,start.plusSeconds(3600),null)),0,now.plusSeconds(900));
        service=new CalendarService(store,google,mapper,Clock.fixed(now,ZoneOffset.UTC));
        when(store.settings(owner)).thenReturn(Optional.of(mapper.writeValueAsString(settings)));
        when(store.preview(owner,previewId)).thenReturn(Optional.of(mapper.writeValueAsString(preview)));
        when(store.tasks(eq(owner),any(),any())).thenReturn(List.of(task));when(store.exported(owner)).thenReturn(Set.of());when(store.attempts(owner)).thenReturn(List.of());
        when(google.calendars(owner)).thenReturn(List.of(new GoogleCalendarClient.CalendarInfo("target","Study",true,true),new GoogleCalendarClient.CalendarInfo("busy","Work",false,false)));
        when(google.busy(eq(owner),anyList(),any(),any(),anyString())).thenReturn(List.of());
        when(google.event(eq(owner),anyString(),anyString())).thenReturn(Optional.empty());
    }
    JsonNode event() {
        var e=mapper.createObjectNode().put("id",CalendarService.eventId(owner,task.id())).put("htmlLink","https://calendar.google.com/example");
        e.putObject("start").put("dateTime",start.toString());e.putObject("end").put("dateTime",start.plusSeconds(3600).toString());
        e.putObject("extendedProperties").putObject("private").put("studyPlannerTask",task.id().toString()).put("studyPlannerOwner",owner.toString());return e;
    }
    @Test void rechecksBusyBeforeWritingAndRejectsNewConflict() {
        when(google.busy(eq(owner),anyList(),any(),any(),anyString())).thenReturn(List.of(new Busy(start,start.plusSeconds(600))));
        var result=service.confirm(owner,previewId);assertEquals(1,result.failed());verify(google,never()).insert(any(),any(),any());verify(store,never()).begin(any(),any(),any(),any(),any(),any(),any());
    }
    @Test void foreignOrExpiredPreviewCannotBeExported() {
        when(store.preview(owner,previewId)).thenReturn(Optional.empty());assertThrows(PlanException.class,()->service.confirm(owner,previewId));verifyNoInteractions(google);
    }
    @Test void staleCompletedOrDeletedTaskCannotBeExported() {
        when(store.tasks(eq(owner),any(),any())).thenReturn(List.of());assertEquals(1,service.confirm(owner,previewId).failed());verify(google,never()).insert(any(),any(),any());
    }
    @Test void changedSettingsInvalidatePreview() throws Exception {
        when(store.settings(owner)).thenReturn(Optional.of(mapper.writeValueAsString(defaults())));
        assertThrows(PlanException.class,()->service.confirm(owner,previewId));verify(google,never()).insert(any(),any(),any());
    }
    @Test void ambiguousPreviousInsertIsRecoveredWithoutWritingAnotherEvent() {
        when(google.event(eq(owner),anyString(),anyString())).thenReturn(Optional.of(event()));
        var result=service.confirm(owner,previewId);assertEquals(1,result.confirmed());verify(google,never()).insert(any(),any(),any());verify(store).confirmed(eq(owner),eq(task.id()),anyString());
    }
    @Test void existingEventThatWasMovedExternallyIsNotOverwrittenOrDuplicated() {
        var e=event();((com.fasterxml.jackson.databind.node.ObjectNode)e.path("start")).put("dateTime",start.plusSeconds(900).toString());
        when(google.event(eq(owner),anyString(),anyString())).thenReturn(Optional.of(e));
        assertEquals(1,service.confirm(owner,previewId).failed());verify(google,never()).insert(any(),any(),any());
    }
    @Test void pendingReceiptIsDurableBeforeGoogleInsertionAndConfirmation() {
        when(google.insert(eq(owner),eq("target"),any())).thenReturn(event());
        assertEquals(1,service.confirm(owner,previewId).confirmed());
        var order=inOrder(store,google);order.verify(store).begin(eq(owner),eq(task.id()),eq("target"),anyString(),eq(start),any(),anyString());
        order.verify(google).insert(eq(owner),eq("target"),any());order.verify(store).confirmed(eq(owner),eq(task.id()),anyString());
    }
    @Test void googleFailureDoesNotFabricateAReceipt() {
        when(google.insert(any(),any(),any())).thenThrow(new PlanException(502,"CALENDAR_UNAVAILABLE","Try again"));
        assertEquals(1,service.confirm(owner,previewId).failed());verify(store,never()).confirmed(any(),any(),any());
    }
    @Test void confirmedReceiptMakesRetryIdempotent() {
        when(store.exported(owner)).thenReturn(Set.of(task.id()));assertEquals(1,service.confirm(owner,previewId).confirmed());verify(google,never()).insert(any(),any(),any());
    }
    @Test void missingSelectedCalendarIsReportedExplicitlyAndNeverTreatedAsFree() throws Exception {
        var saved=new Settings(settings.timezone(),settings.days(),settings.blocks(),10,List.of("busy","no-longer-visible"),"target");
        when(store.settings(owner)).thenReturn(Optional.of(mapper.writeValueAsString(saved)));
        var ex=assertThrows(PlanException.class,()->service.preview(owner,date,null));
        assertEquals(1,ex.context().get("unavailableCalendarCount"));
        assertTrue(ex.getMessage().contains("không còn"));
        verify(google,never()).busy(any(),anyList(),any(),any(),anyString());
    }
}
