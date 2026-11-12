package com.studyplanner.sessions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.auth.AuthUserRepository;
import com.studyplanner.auth.SecurityConfig;
import com.studyplanner.sessions.StudySessionPolicy.Status;

/** Contract tests for the study-session endpoints: statuses, envelope, strict bodies, error codes. */
@WebMvcTest(controllers = StudySessionController.class)
@Import(SecurityConfig.class)
class StudySessionControllerTest {
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TASK = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String KEY = "44444444-4444-4444-8444-444444444444";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private StudySessionService service;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private AuthUserRepository authUserRepository;

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b) {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        return b.with(authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities())));
    }

    private static StudySessionService.SessionView view(Status status, int version) {
        Instant now = Instant.parse("2026-11-11T02:02:00Z");
        return new StudySessionService.SessionView(SESSION, TASK, UUID.randomUUID(), "Ôn kiểm thử", "Kiểm tra phần mềm",
                status, 1500, 120, 1380, false, now.minusSeconds(120), now.minusSeconds(120), null, now, version);
    }

    private static MockHttpServletRequestBuilder write(String url, String body, String key) {
        var request = asUser(post(url)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    @Test
    void everyRouteRequiresASession() throws Exception {
        mvc.perform(get("/api/v1/study-sessions/active")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/study-sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/goals/" + TASK + "/study-time")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void writesWithoutCsrfAreForbidden() throws Exception {
        mvc.perform(asUser(post("/api/v1/study-sessions")).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", KEY).content("{\"taskId\":\"" + TASK + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void noActiveSessionIsAnObjectWithANullSessionNotANullData() throws Exception {
        when(service.active(USER)).thenReturn(new StudySessionService.ActiveSessionView(null));
        mvc.perform(asUser(get("/api/v1/study-sessions/active"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session").value((Object) null))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void startReturns201WithTheSessionAndThe200ReplayWhenTheKeyRepeats() throws Exception {
        when(service.start(any(), any(), any())).thenReturn(new StudySessionService.WriteResult(view(Status.RUNNING, 0), false));
        mvc.perform(write("/api/v1/study-sessions", "{\"taskId\":\"" + TASK + "\"}", KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.plannedFocusSeconds").value(1500))
                .andExpect(jsonPath("$.data.endedAt").value((Object) null))
                .andExpect(jsonPath("$.data.userId").doesNotExist());
        when(service.start(any(), any(), any())).thenReturn(new StudySessionService.WriteResult(view(Status.RUNNING, 0), true));
        mvc.perform(write("/api/v1/study-sessions", "{\"taskId\":\"" + TASK + "\"}", KEY))
                .andExpect(status().isOk());
    }

    @Test
    void startRejectsMissingOrMalformedKeysAndStrictBodyViolationsWith400() throws Exception {
        mvc.perform(write("/api/v1/study-sessions", "{\"taskId\":\"" + TASK + "\"}", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("Idempotency-Key"));
        mvc.perform(write("/api/v1/study-sessions", "{\"taskId\":\"" + TASK + "\"}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{}", "{\"taskId\":null}", "{\"taskId\":7}", "{\"taskId\":\"x\"}", "[]",
                "{\"taskId\":\"" + TASK + "\",\"userId\":\"" + USER + "\"}",
                "{\"taskId\":\"" + TASK + "\",\"elapsedSeconds\":1500}")) {
            mvc.perform(write("/api/v1/study-sessions", body, KEY)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void actionRejectsUnknownActionsClientTimeAndBadVersions() throws Exception {
        String url = "/api/v1/study-sessions/" + SESSION + "/actions";
        for (String body : List.of("{}", "{\"action\":\"PAUSE\"}", "{\"expectedVersion\":0}",
                "{\"action\":\"STOP\",\"expectedVersion\":0}", "{\"action\":\"pause\",\"expectedVersion\":0}",
                "{\"action\":\"PAUSE\",\"expectedVersion\":-1}", "{\"action\":\"PAUSE\",\"expectedVersion\":\"0\"}",
                "{\"action\":\"PAUSE\",\"expectedVersion\":1.5}",
                "{\"action\":\"FINISH\",\"expectedVersion\":0,\"elapsedSeconds\":1500}")) {
            mvc.perform(write(url, body, KEY)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void actionReturnsTheNewSessionAndMapsDomainErrorsToTheirCodes() throws Exception {
        String url = "/api/v1/study-sessions/" + SESSION + "/actions";
        String body = "{\"action\":\"PAUSE\",\"expectedVersion\":0}";
        when(service.action(any(), any(), any(), any()))
                .thenReturn(new StudySessionService.WriteResult(view(Status.PAUSED, 1), false));
        mvc.perform(write(url, body, KEY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED")).andExpect(jsonPath("$.data.version").value(1));

        doThrow(StudySessionException.versionConflict(2)).when(service).action(any(), any(), any(), any());
        mvc.perform(write(url, body, KEY)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.context.currentVersion").value(2));

        doThrow(StudySessionException.finishRequired()).when(service).action(any(), any(), any(), any());
        mvc.perform(write(url, body, KEY)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FOCUS_FINISH_REQUIRED"));

        doThrow(StudySessionException.notFound()).when(service).action(any(), any(), any(), any());
        mvc.perform(write(url, body, KEY)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void startConflictCarriesTheActiveSessionIdOfTheCaller() throws Exception {
        when(service.start(any(), any(), any())).thenThrow(StudySessionException.alreadyActive(SESSION));
        mvc.perform(write("/api/v1/study-sessions", "{\"taskId\":\"" + TASK + "\"}", KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_ALREADY_ACTIVE"))
                .andExpect(jsonPath("$.error.context.activeSessionId").value(SESSION.toString()));
    }

    @Test
    void aMalformedSessionIdInThePathIsNotFound() throws Exception {
        mvc.perform(asUser(get("/api/v1/study-sessions/not-a-uuid"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void historyIsPagedWithMetaAndValidatesQueryParameters() throws Exception {
        when(service.list(any(), any(), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(new StudySessionService.SessionPage(List.of(view(Status.COMPLETED, 3)), 41));
        mvc.perform(asUser(get("/api/v1/study-sessions"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.meta.totalItems").value(41))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
        mvc.perform(asUser(get("/api/v1/study-sessions?limit=101"))).andExpect(status().isBadRequest());
        mvc.perform(asUser(get("/api/v1/study-sessions?taskId=abc"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("taskId"));
    }

    @Test
    void studyTimeReturnsCountAndSecondsAndHidesForeignGoals() throws Exception {
        when(service.studyTime(USER, TASK)).thenReturn(new StudySessionRepository.StudyTime(2, 1800));
        mvc.perform(asUser(get("/api/v1/goals/" + TASK + "/study-time"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedSessionCount").value(2))
                .andExpect(jsonPath("$.data.totalFocusSeconds").value(1800));
        doThrow(StudySessionException.goalNotFound()).when(service).studyTime(USER, SESSION);
        mvc.perform(asUser(get("/api/v1/goals/" + SESSION + "/study-time"))).andExpect(status().isNotFound());
    }
}
