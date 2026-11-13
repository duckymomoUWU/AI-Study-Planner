package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

/** Contract tests for /api/v1/goals: statuses, envelope, error codes (docs/backend-api-rules.md). */
@WebMvcTest(controllers = GoalController.class)
@Import(SecurityConfig.class)
class GoalControllerTest {
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GOAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE = "/api/v1/goals";
    private static final Instant CREATED = Instant.parse("2026-11-07T03:00:00Z");

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

    private static GoalJdbcRepository.StoredGoal stored(int version) {
        return new GoalJdbcRepository.StoredGoal(GOAL, USER, "Toán", "Tích phân",
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 5), 90, "Asia/Ho_Chi_Minh", version, CREATED);
    }

    private static String body(String subject, String start, String exam, String minutes, String extra) {
        return "{\"subject\":" + subject + ",\"topics\":\"Tích phân\",\"startDate\":" + start
                + ",\"examDate\":" + exam + ",\"minutesPerDay\":" + minutes + extra + "}";
    }

    private static String valid() {
        return body("\"Toán\"", "\"2026-11-09\"", "\"2026-12-05\"", "90", "");
    }

    @Test
    @DisplayName("a subject of 101 characters is a 400 too_long on create and patch and never reaches the service; 100 passes validation")
    void subjectLongerThan100CharactersIs400() throws Exception {
        String tooLong = "\"" + "S".repeat(101) + "\"";
        mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(tooLong, "\"2026-11-09\"", "\"2026-12-05\"", "90", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field=='subject' && @.issue=='too_long')]").exists());
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("\"subject\":" + tooLong + ",\"expectedVersion\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='subject' && @.issue=='too_long')]").exists());
        verifyNoInteractions(service);

        // The boundary itself, with surrounding spaces and emoji counted as one character each, is not a length error.
        for (String subject : new String[]{"S".repeat(100), "S".repeat(100) + "   ", "\uD83D\uDE00".repeat(100)}) {
            var result = mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON)
                    .content(body("\"" + subject + "\"", "\"2026-11-09\"", "\"2026-12-05\"", "90", ""))).andReturn();
            org.junit.jupiter.api.Assertions.assertFalse(
                    result.getResponse().getContentAsString().contains("too_long"), "100 characters must not be too_long");
        }
    }

    private static String patchBody(String fields) {
        return "{" + fields + "}";
    }

    // ---------- authentication and CSRF

    @Test
    @DisplayName("without a session every route is 401 UNAUTHORIZED and never reaches the service")
    void unauthenticated() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get(BASE + "/" + GOAL)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(BASE + "/" + GOAL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(patchBody("\"expectedVersion\":0,\"minutesPerDay\":60"))).andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/" + GOAL).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void writesWithoutCsrfAreForbidden() throws Exception {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        var auth = authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        mvc.perform(post(BASE).with(auth).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(patch(BASE + "/" + GOAL).with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(patchBody("\"expectedVersion\":0,\"minutesPerDay\":60"))).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/" + GOAL).with(auth)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    // ---------- create

    @Test
    void createReturns201WithEnvelopeAndNeverExposesTheOwner() throws Exception {
        when(service.createGoal(eq(USER), any(), eq(null))).thenReturn(stored(0));

        mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(GOAL.toString()))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-07T03:00:00Z"))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void createPassesParsedFieldsAndOptionalTimezoneToService() throws Exception {
        when(service.createGoal(eq(USER), any(), eq("UTC"))).thenReturn(stored(0));
        mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"Toán\"", "\"2026-11-09\"", "\"2026-12-05\"", "90", ",\"timezone\":\"UTC\"")))
                .andExpect(status().isCreated());
        var captor = ArgumentCaptor.forClass(GoalPolicy.GoalInput.class);
        verify(service).createGoal(eq(USER), captor.capture(), eq("UTC"));
        assertEquals(new GoalPolicy.GoalInput("Toán", "Tích phân",
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 5), 90), captor.getValue());
    }

    @Test
    @DisplayName("fields the client may not send are rejected with unknown_field, not silently ignored")
    void createRejectsForbiddenFields() throws Exception {
        for (String field : List.of("userId", "id", "createdAt", "version", "source", "whatever")) {
            String json = body("\"Toán\"", "\"2026-11-09\"", "\"2026-12-05\"", "90",
                    ",\"" + field + "\":\"99999999-9999-9999-9999-999999999999\"");
            mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[?(@.field=='" + field + "' && @.issue=='unknown_field')]")
                            .exists());
        }
        verifyNoInteractions(service);
    }

    @Test
    void missingBlankAndWrongTypedFieldsAreReportedPerField() throws Exception {
        String bad = "{\"subject\":null,\"topics\":5,\"startDate\":\"09/11/2026\",\"examDate\":\"\",\"minutesPerDay\":\"90\"}";
        mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field=='subject' && @.issue=='required')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='topics' && @.issue=='invalid_type')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='startDate' && @.issue=='invalid_date')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='examDate' && @.issue=='required')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='minutesPerDay')]").exists());
        verifyNoInteractions(service);
    }

    @Test
    void decimalMinutesAreRejected() throws Exception {
        mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"Toán\"", "\"2026-11-09\"", "\"2026-12-05\"", "90.5", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("minutesPerDay"));
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonAndNonObjectBodiesAre400() throws Exception {
        mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].issue").value("malformed_json"));
        mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON).content("[1,2]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='body' && @.issue=='invalid_type')]").exists());
        verifyNoInteractions(service);
    }

    @Test
    void serviceValidationErrorsAre400WithIssueNames() throws Exception {
        when(service.createGoal(eq(USER), any(), eq(null))).thenThrow(new GoalService.GoalValidationException(List.of(
                new GoalPolicy.Violation("examDate", "exceeds_90_days", "x"))));
        mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("examDate"))
                .andExpect(jsonPath("$.error.details[0].issue").value("exceeds_90_days"));
    }

    @Test
    void blankDatesAreReportedAsRequiredNotAsBadFormat() throws Exception {
        String bad = "{\"subject\":\"\",\"topics\":\"   \",\"startDate\":\"\",\"examDate\":\"\",\"minutesPerDay\":90}";
        // Blank text parses cleanly, so the real service applies GoalPolicy; mimic that here.
        when(service.createGoal(eq(USER), any(), eq(null))).thenAnswer(invocation -> {
            var input = (GoalPolicy.GoalInput) invocation.getArgument(1);
            throw new GoalService.GoalValidationException(GoalPolicy.validate(input, LocalDate.of(2026, 11, 7)));
        });
        mvc.perform(asUser(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='startDate' && @.issue=='required')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='examDate' && @.issue=='required')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='startDate' && @.issue=='invalid_date')]")
                        .doesNotExist());
    }

    // ---------- list and detail

    @Test
    void listUsesEnvelopeWithMetaAndDefaultPaging() throws Exception {
        when(service.listGoals(USER, 1, 20)).thenReturn(new GoalService.GoalPage(List.of(stored(0)), 41));
        mvc.perform(asUser(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].subject").value("Toán"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.totalItems").value(41))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
    }

    @Test
    void listPassesPageAndLimitAndReturnsEmptyData() throws Exception {
        when(service.listGoals(USER, 2, 100)).thenReturn(new GoalService.GoalPage(List.of(), 0));
        mvc.perform(asUser(get(BASE + "?page=2&limit=100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void invalidPageOrLimitIs400() throws Exception {
        for (String query : List.of("?page=0", "?limit=0", "?limit=101", "?page=-1", "?page=abc", "?limit=x")) {
            mvc.perform(asUser(get(BASE + query)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void getOwnGoalIs200InEnvelope() throws Exception {
        when(service.getGoal(USER, GOAL)).thenReturn(stored(0));
        mvc.perform(asUser(get(BASE + "/" + GOAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minutesPerDay").value(90));
    }

    @Test
    void getOtherOwnersUnknownOrMalformedIdIs404NotFound() throws Exception {
        when(service.getGoal(USER, GOAL)).thenThrow(new GoalService.GoalNotFoundException());
        mvc.perform(asUser(get(BASE + "/" + GOAL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(asUser(get(BASE + "/not-a-uuid")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ---------- patch

    @Test
    void patchSendsOnlyTheChangedFieldsPlusExpectedVersion() throws Exception {
        when(service.patchGoal(eq(USER), eq(GOAL), any(), eq(0))).thenReturn(stored(1));
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("\"minutesPerDay\":120,\"timezone\":\"UTC\",\"expectedVersion\":0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(GOAL.toString()))
                .andExpect(jsonPath("$.data.version").value(1));
        var captor = ArgumentCaptor.forClass(GoalPatch.class);
        verify(service).patchGoal(eq(USER), eq(GOAL), captor.capture(), eq(0));
        assertEquals(new GoalPatch(null, null, null, null, 120, "UTC"), captor.getValue());
    }

    @Test
    void patchRequiresExpectedVersionAndAtLeastOneField() throws Exception {
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("\"minutesPerDay\":60")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='expectedVersion' && @.issue=='required')]").exists());
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("\"expectedVersion\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='body' && @.issue=='empty_patch')]").exists());
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("\"minutesPerDay\":60,\"expectedVersion\":\"0\"")))
                .andExpect(status().isBadRequest());
        verify(service, never()).patchGoal(any(), any(), any(), anyInt());
    }

    @Test
    void patchRejectsNullOnRequiredFieldsAndForbiddenFields() throws Exception {
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("\"subject\":null,\"expectedVersion\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='subject' && @.issue=='required')]").exists());
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("\"userId\":\"x\",\"version\":3,\"minutesPerDay\":60,\"expectedVersion\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='userId' && @.issue=='unknown_field')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='version' && @.issue=='unknown_field')]").exists());
        verify(service, never()).patchGoal(any(), any(), any(), anyInt());
    }

    @Test
    void patchMapsNotFoundVersionConflictAndTaskConflict() throws Exception {
        String json = patchBody("\"minutesPerDay\":60,\"expectedVersion\":0");

        when(service.patchGoal(eq(USER), eq(GOAL), any(), eq(0))).thenThrow(new GoalService.GoalNotFoundException());
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        when(service.patchGoal(eq(USER), eq(GOAL), any(), eq(0)))
                .thenThrow(new GoalService.GoalConflictException("stale_goal_version"));
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOAL_VERSION_CONFLICT"));

        when(service.patchGoal(eq(USER), eq(GOAL), any(), eq(0)))
                .thenThrow(new GoalService.GoalValidationException(List.of(
                        new GoalPolicy.Violation("minutesPerDay", "daily_budget_exceeded", "Ngày 2026-11-20 vượt"))));
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOAL_UPDATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].issue").value("daily_budget_exceeded"))
                .andExpect(jsonPath("$.error.details[0].message").value("Ngày 2026-11-20 vượt"));
    }

    @Test
    void invalidInputMixedWithTaskConflictsIs400() throws Exception {
        when(service.patchGoal(eq(USER), eq(GOAL), any(), eq(0)))
                .thenThrow(new GoalService.GoalValidationException(List.of(
                        new GoalPolicy.Violation("examDate", "exceeds_90_days", "x"),
                        new GoalPolicy.Violation("minutesPerDay", "daily_budget_exceeded", "y"))));
        mvc.perform(asUser(patch(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("\"minutesPerDay\":60,\"expectedVersion\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void putIsNoLongerSupported() throws Exception {
        mvc.perform(asUser(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(BASE + "/" + GOAL)).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
        verifyNoInteractions(service);
    }

    // ---------- delete

    @Test
    void deleteReturns204UsingThePrincipalAsOwner() throws Exception {
        mvc.perform(asUser(delete(BASE + "/" + GOAL))).andExpect(status().isNoContent());
        verify(service).deleteGoal(USER, GOAL);
    }

    @Test
    void deleteOfUnknownOtherOwnersOrMalformedIdIs404() throws Exception {
        doThrow(new GoalService.GoalNotFoundException()).when(service).deleteGoal(USER, GOAL);
        mvc.perform(asUser(delete(BASE + "/" + GOAL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(asUser(delete(BASE + "/not-a-uuid"))).andExpect(status().isNotFound());
    }

    // ---------- unexpected

    @Test
    void unexpectedErrorsAre500WithoutDetails() throws Exception {
        when(service.listGoals(USER, 1, 20)).thenThrow(new IllegalStateException("secret db detail"));
        mvc.perform(asUser(get(BASE)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }
}
