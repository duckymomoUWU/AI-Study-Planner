package com.studyplanner.calendar;

import static org.junit.jupiter.api.Assertions.*;
import static com.studyplanner.calendar.CalendarScheduling.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import com.studyplanner.goals.PlanException;

class CalendarSchedulingTest {
    final LocalDate day=LocalDate.of(2026,11,11);
    final ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
    Instant at(String hour) { return day.atTime(LocalTime.parse(hour)).atZone(zone).toInstant(); }
    Task task(int minutes) { return new Task(UUID.randomUUID(),UUID.randomUUID(),"Topic","Math",day,minutes,false,day.plusDays(10)); }
    Settings hours(String start,String end,List<Block> blocks,int gap) {
        var base=defaults(); return new Settings(base.timezone(),base.days().stream().map(d->new DayHours(d.weekday(),true,start,end)).toList(),blocks,gap,List.of("work"),"study");
    }
    @Test void avoidsRemoteBusyLunchSleepAndKeepsWholeSession() {
        var s=hours("06:00","22:00",defaults().blocks(),10);
        var slots=schedule(s,List.of(task(90)),List.of(new Busy(at("08:00"),at("11:30"))),at("06:00"));
        // 11:40 + 90 minutes crosses lunch; next contiguous slot begins at 13:00.
        assertEquals(at("13:00"),slots.getFirst().start()); assertEquals(at("14:30"),slots.getFirst().end());
    }
    @Test void overnightSleepFromYesterdayBlocksEarlyMorning() {
        var s=hours("00:00","22:00",List.of(new Block("Sleep","23:00","07:00",List.of(2))),0);
        var slot=schedule(s,List.of(task(60)),List.of(),at("00:00")).getFirst();
        assertEquals(at("07:00"),slot.start());
    }
    @Test void insufficientContiguousTimeNeverSplitsOrMovesToNextDay() {
        var s=hours("08:00","10:00",List.of(),0);
        var slot=schedule(s,List.of(task(90)),List.of(new Busy(at("08:45"),at("09:15"))),at("07:00")).getFirst();
        assertNull(slot.start()); assertNotNull(slot.reason()); assertEquals(day,slot.task().date());
    }
    @Test void placesAcrossGoalsWithoutOverlapAndAddsRestOnBothSidesOfEvents() {
        var s=hours("08:00","12:00",List.of(),10);
        var slots=schedule(s,List.of(task(60),task(60)),List.of(new Busy(at("10:30"),at("11:00"))),at("07:00"));
        assertEquals(at("08:00"),slots.get(0).start()); assertEquals(at("09:10"),slots.get(1).start());
        assertEquals(at("10:10"),slots.get(1).end());
    }
    @Test void excludesCompletedPastDaysAndStudyOnExamDay() {
        var base=task(60);
        var completed=new Task(base.id(),base.goalId(),base.title(),base.subject(),day,60,true,base.examDate());
        var past=new Task(UUID.randomUUID(),base.goalId(),"Past","Math",day.minusDays(1),60,false,base.examDate());
        var exam=new Task(UUID.randomUUID(),base.goalId(),"Exam","Math",day,60,false,day);
        var result=schedule(defaults(),List.of(completed,past,exam),List.of(),at("07:00"));
        assertEquals(1,result.size()); assertNull(result.getFirst().start());
    }
    @Test void neverStartsInPastAndAcceptsExactGapBoundary() {
        var result=schedule(hours("08:00","10:00",List.of(),0),List.of(task(30)),List.of(new Busy(at("08:00"),at("09:00"))),at("08:30"));
        assertEquals(at("09:00"),result.getFirst().start());
    }
    @Test void disabledWeekdayHasNoPlacement() {
        var s=defaults(); var disabled=new Settings(s.timezone(),s.days().stream().map(d->new DayHours(d.weekday(),false,d.start(),d.end())).toList(),s.blocks(),0,List.of(),"");
        assertNull(schedule(disabled,List.of(task(60)),List.of(),at("07:00")).getFirst().start());
    }
    @Test void subSecondNowAndEventEndsRoundUpWithoutCreatingAConflict() {
        var s=hours("08:00","10:00",List.of(),0);
        assertEquals(at("08:01"),schedule(s,List.of(task(30)),List.of(),at("08:00").plusNanos(500000000)).getFirst().start());
        var result=schedule(s,List.of(task(30)),List.of(new Busy(at("08:00"),at("08:30").plusNanos(100)),new Busy(at("09:00"),at("09:10"))),at("07:00"));
        assertEquals(at("09:10"),result.getFirst().start());
    }
    @Test void rejectsEqualOrMalformedTimesAndDuplicateDays() {
        assertThrows(PlanException.class,()->validate(hours("08:00","08:00",List.of(),0)));
        assertThrows(PlanException.class,()->validate(hours("99:00","22:00",List.of(),0)));
        var s=defaults(); assertThrows(PlanException.class,()->validate(new Settings(s.timezone(),Collections.nCopies(7,s.days().getFirst()),s.blocks(),0,List.of(),"")));
    }
    @Test void eventIdsAreStablePerOwnerAndTaskAndValidGoogleBase32hexCharacters() {
        var owner=UUID.randomUUID();var id=UUID.randomUUID();String result=CalendarService.eventId(owner,id);
        assertEquals(result,CalendarService.eventId(owner,id));assertTrue(result.matches("[0-9a-v]{5,1024}"));
        assertNotEquals(result,CalendarService.eventId(UUID.randomUUID(),id));
    }
}
