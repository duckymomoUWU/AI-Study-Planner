package com.studyplanner.goals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
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

/**
 * The AI endpoints of a goal (syllabus autofill, plan drafts) moved to /api/v1/goals and the shared envelope when the
 * sprint 3 work of dev1 was merged with the v1 contract. These tests pin that contract: sessions, ownership and the
 * error envelope. The Gemini calls themselves are covered by GeminiPlanServiceTest.
 */
@WebMvcTest(controllers = PlanDraftController.class)
@Import(SecurityConfig.class)
class PlanDraftControllerTest {
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GOAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DRAFT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String BASE = "/api/v1/goals/" + GOAL;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GoalService goalService;
    @MockBean
    private PlanDraftRepository planDraftRepository;
    @MockBean
    private GeminiPlanService geminiPlanService;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private AuthUserRepository authUserRepository;

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b) {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        return b.with(authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()))).with(csrf());
    }

    @Test
    void withoutASessionEveryDraftRouteIs401InTheEnvelope() throws Exception {
        mvc.perform(post(BASE + "/plan-drafts").with(csrf())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get(BASE + "/plan-drafts/latest")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/goals/plan-drafts/" + DRAFT)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/plan-drafts/" + DRAFT + "/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(goalService, planDraftRepository, geminiPlanService);
    }

    @Test
    void theOldUnversionedPathsAreGone() throws Exception {
        mvc.perform(asUser(post("/api/goals/" + GOAL + "/plan-drafts"))).andExpect(status().is4xxClientError());
        verifyNoInteractions(planDraftRepository, geminiPlanService);
    }

    @Test
    void confirmWithoutCsrfIsForbidden() throws Exception {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        mvc.perform(post(BASE + "/plan-drafts/" + DRAFT + "/confirm")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tasks\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        verifyNoInteractions(planDraftRepository);
    }

    @Test
    void noDraftYetIsTheSharedNotFoundNotAnOldStyleCode() throws Exception {
        when(planDraftRepository.findLatestDraft(any(), any())).thenReturn(Optional.empty());
        mvc.perform(asUser(get(BASE + "/plan-drafts/latest")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").doesNotExist());
        when(planDraftRepository.findDraftById(any(), any())).thenReturn(Optional.empty());
        mvc.perform(asUser(get("/api/v1/goals/plan-drafts/" + DRAFT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void aGoalTheCallerDoesNotOwnIsTheSharedNotFound() throws Exception {
        when(goalService.getGoal(USER, GOAL)).thenThrow(new GoalService.GoalNotFoundException());
        mvc.perform(asUser(post(BASE + "/plan-drafts")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(asUser(get(BASE + "/plan-drafts/latest")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        verifyNoInteractions(geminiPlanService);
    }

    @Test
    void confirmWithoutTasksIsAValidationErrorWithFieldDetails() throws Exception {
        mvc.perform(asUser(post(BASE + "/plan-drafts/" + DRAFT + "/confirm"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedGoalVersion\":0,\"tasks\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("tasks"));
        verifyNoInteractions(planDraftRepository);
    }
}
