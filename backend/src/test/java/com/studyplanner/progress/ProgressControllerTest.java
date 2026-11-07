package com.studyplanner.progress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.auth.AuthUserRepository;
import com.studyplanner.auth.SecurityConfig;
import com.studyplanner.goals.GoalService;

/** Contract tests for GET /api/v1/goals/{goalId}/progress (docs/backend-api-rules.md). */
@WebMvcTest(controllers = ProgressController.class)
@Import(SecurityConfig.class)
class ProgressControllerTest {
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GOAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String URL = "/api/v1/goals/" + GOAL + "/progress";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ProgressService service;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private AuthUserRepository authUserRepository;

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b) {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        return b.with(authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities())));
    }

    @Test
    void returnsTheCountsInTheEnvelopeUsingThePrincipalAsOwner() throws Exception {
        when(service.getProgress(USER, GOAL))
                .thenReturn(new ProgressService.Progress(GOAL, 3, 1, 2, 1.0 / 3));
        mvc.perform(asUser(get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalId").value(GOAL.toString()))
                .andExpect(jsonPath("$.data.totalTasks").value(3))
                .andExpect(jsonPath("$.data.completedTasks").value(1))
                .andExpect(jsonPath("$.data.remainingTasks").value(2))
                .andExpect(jsonPath("$.data.completionRate").value(1.0 / 3))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void aGoalWithoutTasksIsZeroOfZeroWithRateZero() throws Exception {
        when(service.getProgress(USER, GOAL)).thenReturn(new ProgressService.Progress(GOAL, 0, 0, 0, 0.0));
        mvc.perform(asUser(get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTasks").value(0))
                .andExpect(jsonPath("$.data.completedTasks").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(0.0));
    }

    @Test
    void withoutASessionIs401AndTheServiceIsNotCalled() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test
    void unknownForeignOrMalformedGoalIsTheSame404() throws Exception {
        when(service.getProgress(USER, GOAL)).thenThrow(new GoalService.GoalNotFoundException());
        mvc.perform(asUser(get(URL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(asUser(get("/api/v1/goals/not-a-uuid/progress")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void onlyReadingIsAllowed() throws Exception {
        mvc.perform(asUser(post(URL)).with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
        verifyNoInteractions(service);
    }

    @Test
    void unexpectedErrorsAre500WithoutDetails() throws Exception {
        when(service.getProgress(any(), any())).thenThrow(new IllegalStateException("secret db detail"));
        mvc.perform(asUser(get(URL)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }
}
