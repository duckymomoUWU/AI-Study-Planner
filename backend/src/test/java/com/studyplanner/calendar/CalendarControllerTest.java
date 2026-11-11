package com.studyplanner.calendar;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockHttpSession;
import com.studyplanner.auth.*;
import java.time.*;
import java.util.*;

@WebMvcTest(CalendarController.class)
@Import(SecurityConfig.class)
class CalendarControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CalendarService service;
    @MockBean GoogleCalendarClient google;
    @MockBean Clock clock;
    @MockBean UserDetailsService users;
    @MockBean AuthUserRepository userRepository;
    UUID owner=UUID.randomUUID(); Instant now=Instant.parse("2026-11-11T00:00:00Z");
    MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        var p=new AppUserPrincipal(owner,"calendar@example.test","unused");
        return request.with(authentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities())));
    }
    @BeforeEach void time() { when(clock.instant()).thenReturn(now); }
    @Test void needsAuthenticationAndCsrfForWrites() throws Exception {
        mvc.perform(get("/api/v1/calendar/status")).andExpect(status().isUnauthorized());
        mvc.perform(authenticated(post("/api/v1/calendar/connection"))).andExpect(status().isForbidden());verifyNoInteractions(google,service);
    }
    @Test void serverOwnerIsPassedAndNoTokensAppearInStatus() throws Exception {
        when(service.status(owner)).thenReturn(Map.of("configured",false,"connected",false,"settings",CalendarScheduling.defaults()));
        mvc.perform(authenticated(get("/api/v1/calendar/status"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.configured").value(false)).andExpect(jsonPath("$.data.access_token").doesNotExist());
        verify(service).status(owner);
    }
    @Test void validOAuthStateIsConsumedAndPkceVerifierExchanged() throws Exception {
        var session=new MockHttpSession(); session.setAttribute("calendar.oauth",new CalendarController.OAuthState(owner,"nonce","verifier",now.plusSeconds(600)));
        when(google.returnUrl(true)).thenReturn("http://127.0.0.1:5173/?calendar=connected#daily");
        mvc.perform(authenticated(get("/api/v1/calendar/oauth/callback").session(session).param("state","nonce").param("code","test-code")))
            .andExpect(status().isSeeOther());verify(google).exchange(owner,"test-code","verifier");
        mvc.perform(authenticated(get("/api/v1/calendar/oauth/callback").session(session).param("state","nonce").param("code","test-code")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("CALENDAR_OAUTH_STATE_INVALID"));
        verify(google,times(1)).exchange(any(),any(),any());
    }
    @Test void wrongOwnerExpiredStateAndWrongNonceNeverExchangeTokens() throws Exception {
        for(var flow:List.of(new CalendarController.OAuthState(UUID.randomUUID(),"nonce","v",now.plusSeconds(600)),
                new CalendarController.OAuthState(owner,"nonce","v",now.minusSeconds(1)),new CalendarController.OAuthState(owner,"different","v",now.plusSeconds(600)))) {
            var session=new MockHttpSession();session.setAttribute("calendar.oauth",flow);
            mvc.perform(authenticated(get("/api/v1/calendar/oauth/callback").session(session).param("state","nonce").param("code","test"))).andExpect(status().isBadRequest());
        }
        verify(google,never()).exchange(any(),any(),any());
    }
    @Test void authorizationCreatesSessionBoundPkceFlow() throws Exception {
        when(google.authorizationUrl(any(),any())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth");
        mvc.perform(authenticated(post("/api/v1/calendar/connection")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value("https://accounts.google.com/o/oauth2/v2/auth"));
        verify(google).authorizationUrl(anyString(),argThat(challenge->challenge.matches("[A-Za-z0-9_-]{43}")));
    }
}
