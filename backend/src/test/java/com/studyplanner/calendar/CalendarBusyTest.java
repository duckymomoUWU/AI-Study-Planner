package com.studyplanner.calendar;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.goals.PlanException;
import java.util.List;

class CalendarBusyTest {
    ObjectMapper mapper=new ObjectMapper();
    @Test void missingOrFailedCalendarNeverCountsAsFree() throws Exception {
        for(String value:List.of("{\"calendars\":{}}","{\"calendars\":{\"work\":{\"errors\":[{\"reason\":\"notFound\"}]}}}")) {
            var json=mapper.readTree(value);var error=assertThrows(PlanException.class,()->GoogleCalendarClient.parseBusy(json,List.of("work")));
            assertEquals(List.of("work"),error.context().get("failedCalendarIds"));
        }
    }
    @Test void readsAllSelectedCalendarsAndRejectsMalformedIntervals() throws Exception {
        var json=mapper.readTree("{\"calendars\":{\"work\":{\"busy\":[{\"start\":\"2026-11-11T09:00:00+07:00\",\"end\":\"2026-11-11T10:00:00+07:00\"}]},\"personal\":{\"busy\":[]}}}");
        assertEquals(1,GoogleCalendarClient.parseBusy(json,List.of("work","personal")).size());
        var bad=mapper.readTree("{\"calendars\":{\"work\":{\"busy\":[{\"start\":\"invalid\",\"end\":\"invalid\"}]}}}");
        assertThrows(PlanException.class,()->GoogleCalendarClient.parseBusy(bad,List.of("work")));
    }
}
