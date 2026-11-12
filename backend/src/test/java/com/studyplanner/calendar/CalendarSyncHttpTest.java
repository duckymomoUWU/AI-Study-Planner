package com.studyplanner.calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.goals.PlanException;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

class CalendarSyncHttpTest {
    CalendarStore store=mock(CalendarStore.class);CalendarTokenVault vault=mock(CalendarTokenVault.class);HttpClient http=mock(HttpClient.class);
    ObjectMapper mapper=new ObjectMapper();UUID owner=UUID.randomUUID();GoogleCalendarClient client;
    @SuppressWarnings("unchecked") HttpResponse<String> response=mock(HttpResponse.class);
    @BeforeEach void setup()throws Exception {
        client=new GoogleCalendarClient(store,vault,mapper,"test-client","test-secret","http://localhost/callback","http://localhost",http);
        when(vault.configured()).thenReturn(true);when(store.tokens(owner)).thenReturn(Optional.of("sealed"));
        when(vault.open(owner,"sealed")).thenReturn("{\"access_token\":\"test-token\",\"expires_at\":\""+Instant.now().plusSeconds(3600)+"\"}");
        doReturn(response).when(http).send(any(HttpRequest.class),any());
    }
    @Test void conditionalPatchUsesStableEncodedEventPath() {
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"id\":\"existing-event\"}");
        assertEquals("existing-event",client.patch(owner,"a@example.test","existing-event",mapper.createObjectNode().put("summary","Changed"),"\"etag-1\"").path("id").asText());
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        try {verify(http).send(request.capture(),any());}catch(Exception ex){throw new AssertionError(ex);}
        assertEquals("PATCH",request.getValue().method());assertTrue(request.getValue().uri().toString().endsWith("/calendars/a%40example.test/events/existing-event"));
        assertEquals("\"etag-1\"",request.getValue().headers().firstValue("If-Match").orElseThrow());
    }
    @Test void deleteAccepts204AndAlreadyMissingWithoutParsingAnEmptyBody() {
        for(int code:List.of(204,404,410)) {when(response.statusCode()).thenReturn(code);when(response.body()).thenReturn("");assertDoesNotThrow(()->client.delete(owner,"target","id","etag"));}
    }
    @Test void preconditionFailureRemainsARetryableConflict() {
        when(response.statusCode()).thenReturn(412);when(response.body()).thenReturn("private provider response");
        var error=assertThrows(PlanException.class,()->client.delete(owner,"target","id","etag"));
        assertEquals("CALENDAR_REMOTE_CHANGED",error.code());assertFalse(error.getMessage().contains("private provider"));
    }
}
