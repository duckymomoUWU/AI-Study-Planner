package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(controllers = GoalController.class)
@Import({SecurityConfig.class, GoalExceptionHandler.class})
class GoalControllerTest {
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GOAL = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GoalService service;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private AuthUserRepository authUserRepository;

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b) {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        return b.with(authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()))).with(csrf());
    }

    private static GoalJdbcRepository.StoredGoal stored() {
        return new GoalJdbcRepository.StoredGoal(GOAL, USER, "Toán", "Tích phân",
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 5), 90, "Asia/Ho_Chi_Minh", 0);
    }

    private static String body(String subject, String start, String exam, String minutes, String extra) {
        return "{\"subject\":" + subject + ",\"topics\":\"Tích phân\",\"startDate\":" + start
                + ",\"examDate\":" + exam + ",\"minutesPerDay\":" + minutes + extra + "}";
    }

    private static String valid() {
        return body("\"Toán\"", "\"2026-11-09\"", "\"2026-12-05\"", "90", "");
    }

    @Test
    @DisplayName("unauthenticated requests get 401 and never reach the service")
    void unauthenticated() throws Exception {
        mvc.perform(get("/api/goals")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
        mvc.perform(get("/api/goals/" + GOAL)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/goals").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void createReturns201AndIgnoresClientUserId() throws Exception {
        when(service.createGoal(eq(USER), any())).thenReturn(stored());
        String evil = body("\"Toán\"", "\"2026-11-09\"", "\"2026-12-05\"", "90",
                ",\"userId\":\"99999999-9999-9999-9999-999999999999\",\"timezone\":\"UTC\"");

        mvc.perform(asUser(post("/api/goals")).contentType(MediaType.APPLICATION_JSON).content(evil))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(GOAL.toString()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
        verify(service).createGoal(eq(USER), any());
    }

    @Test
    void createPassesParsedFieldsToService() throws Exception {
        when(service.createGoal(eq(USER), any())).thenReturn(stored());
        mvc.perform(asUser(post("/api/goals")).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isCreated());
        var captor = ArgumentCaptor.forClass(GoalPolicy.GoalInput.class);
        verify(service).createGoal(eq(USER), captor.capture());
        assertEquals(new GoalPolicy.GoalInput("Toán", "Tích phân",
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 5), 90), captor.getValue());
    }

    @Test
    void createWithoutCsrfIsForbidden() throws Exception {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        mvc.perform(post("/api/goals").with(authentication(new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("service validation errors map to 400 with field errors")
    void serviceValidationIs400() throws Exception {
        when(service.createGoal(eq(USER), any())).thenThrow(new GoalService.GoalValidationException(List.of(
                new GoalPolicy.Violation("examDate", "over_90_days", "x"))));
        mvc.perform(asUser(post("/api/goals")).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("examDate"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("over_90_days"));
    }

    @Test
    void missingAndWrongTypedFieldsAreRejectedWithoutCallingService() throws Exception {
        String bad = "{\"subject\":null,\"topics\":5,\"startDate\":\"09/11/2026\",\"examDate\":null,\"minutesPerDay\":\"90\"}";
        mvc.perform(asUser(post("/api/goals")).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='subject' && @.code=='required')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='topics' && @.code=='invalid_type')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='startDate' && @.code=='invalid_date')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='examDate' && @.code=='required')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='minutesPerDay')]").exists());
        verifyNoInteractions(service);
    }

    @Test
    void decimalMinutesAreRejected() throws Exception {
        String bad = body("\"Toán\"", "\"2026-11-09\"", "\"2026-12-05\"", "90.5", "");
        mvc.perform(asUser(post("/api/goals")).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("minutesPerDay"));
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(asUser(post("/api/goals")).contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void listReturnsItemsForCurrentUser() throws Exception {
        when(service.listGoals(USER)).thenReturn(List.of(stored()));
        mvc.perform(asUser(get("/api/goals")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].subject").value("Toán"));
        when(service.listGoals(USER)).thenReturn(List.of());
        mvc.perform(asUser(get("/api/goals"))).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void getOwnGoalIs200() throws Exception {
        when(service.getGoal(USER, GOAL)).thenReturn(stored());
        mvc.perform(asUser(get("/api/goals/" + GOAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesPerDay").value(90));
    }

    @Test
    void getOtherOwnersUnknownOrMalformedIdIs404() throws Exception {
        when(service.getGoal(USER, GOAL)).thenThrow(new GoalService.GoalNotFoundException());
        mvc.perform(asUser(get("/api/goals/" + GOAL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("goal_not_found"));
        mvc.perform(asUser(get("/api/goals/not-a-uuid"))).andExpect(status().isNotFound());
    }

    @Test
    void unexpectedErrorsAre500WithoutDetails() throws Exception {
        when(service.listGoals(USER)).thenThrow(new IllegalStateException("secret db detail"));
        mvc.perform(asUser(get("/api/goals")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }

    private static String withVersion(int v) {
        return body("\"Toán\"", "\"2026-11-09\"", "\"2026-12-05\"", "90", ",\"version\":" + v);
    }

    @Test
    void putRequiresAuthenticationAndVersion() throws Exception {
        mvc.perform(put("/api/goals/" + GOAL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(0)))
                .andExpect(status().isUnauthorized());

        mvc.perform(asUser(put("/api/goals/" + GOAL)).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='version' && @.code=='required')]").exists());
        verify(service, never()).updateGoal(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void putSucceedsUsingPrincipalAsOwner() throws Exception {
        when(service.updateGoal(eq(USER), eq(GOAL), any(), eq(0))).thenReturn(stored());
        mvc.perform(asUser(put("/api/goals/" + GOAL)).contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(0).replace("{", "{\"userId\":\"99999999-9999-9999-9999-999999999999\",")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(GOAL.toString()));
        verify(service).updateGoal(eq(USER), eq(GOAL), any(), eq(0));
    }

    @Test
    void putMapsNotFoundStaleVersionAndTaskConflicts() throws Exception {
        when(service.updateGoal(eq(USER), eq(GOAL), any(), eq(0)))
                .thenThrow(new GoalService.GoalNotFoundException());
        mvc.perform(asUser(put("/api/goals/" + GOAL)).contentType(MediaType.APPLICATION_JSON).content(withVersion(0)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("goal_not_found"));

        when(service.updateGoal(eq(USER), eq(GOAL), any(), eq(0)))
                .thenThrow(new GoalService.GoalConflictException("stale_goal_version"));
        mvc.perform(asUser(put("/api/goals/" + GOAL)).contentType(MediaType.APPLICATION_JSON).content(withVersion(0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale_goal_version"));

        when(service.updateGoal(eq(USER), eq(GOAL), any(), eq(0)))
                .thenThrow(new GoalService.GoalValidationException(List.of(
                        new GoalPolicy.Violation("minutesPerDay", "daily_budget_exceeded", "Ngày 2026-11-20 vượt"))));
        mvc.perform(asUser(put("/api/goals/" + GOAL)).contentType(MediaType.APPLICATION_JSON).content(withVersion(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("goal_tasks_conflict"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("daily_budget_exceeded"));
    }

    @Test
    void putWithInputErrorsMixedWithTaskConflictsIs400() throws Exception {
        when(service.updateGoal(eq(USER), eq(GOAL), any(), eq(0)))
                .thenThrow(new GoalService.GoalValidationException(List.of(
                        new GoalPolicy.Violation("examDate", "over_90_days", "x"),
                        new GoalPolicy.Violation("minutesPerDay", "daily_budget_exceeded", "y"))));
        mvc.perform(asUser(put("/api/goals/" + GOAL)).contentType(MediaType.APPLICATION_JSON).content(withVersion(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }
}
