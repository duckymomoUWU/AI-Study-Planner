package com.studyplanner.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
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
import com.studyplanner.goals.GoalService;

/** Contract tests for the task endpoints: statuses, envelope, error codes (docs/backend-api-rules.md). */
@WebMvcTest(controllers = TaskController.class)
@Import(SecurityConfig.class)
class TaskControllerTest {
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GOAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TASK = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String ONE = "/api/v1/goals/" + GOAL + "/tasks/" + TASK;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TaskService service;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private AuthUserRepository authUserRepository;

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b) {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        return b.with(authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()))).with(csrf());
    }

    private static TaskJdbcRepository.StoredTask stored(boolean completed) {
        return new TaskJdbcRepository.StoredTask(TASK, GOAL, "Ôn SQL", LocalDate.of(2026, 11, 10), 30,
                completed, "manual", Instant.parse("2026-11-07T03:00:00Z"));
    }

    private static String completed(String value) {
        return "{\"completed\":" + value + "}";
    }

    @Test
    @DisplayName("without a session every task route is 401 UNAUTHORIZED and never reaches the service")
    void unauthenticated() throws Exception {
        mvc.perform(get("/api/v1/tasks?date=2026-11-10")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/goals/" + GOAL + "/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get(ONE)).andExpect(status().isUnauthorized());
        mvc.perform(patch(ONE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(completed("true")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void patchWithoutCsrfIsForbidden() throws Exception {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        mvc.perform(patch(ONE).with(authentication(new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON).content(completed("true")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void patchMarksDoneAndReturnsTheStoredTaskInEnvelope() throws Exception {
        when(service.setCompleted(USER, GOAL, TASK, true)).thenReturn(stored(true));
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content(completed("true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TASK.toString()))
                .andExpect(jsonPath("$.data.goalId").value(GOAL.toString()))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.source").value("manual"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-07T03:00:00Z"))
                .andExpect(jsonPath("$.meta").doesNotExist());
        verify(service).setCompleted(USER, GOAL, TASK, true);
    }

    @Test
    void patchFalseReopensTheTaskUsingThePrincipalAsOwner() throws Exception {
        when(service.setCompleted(USER, GOAL, TASK, false)).thenReturn(stored(false));
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content(completed("false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(false));
        verify(service).setCompleted(eq(USER), eq(GOAL), eq(TASK), eq(false));
    }

    @Test
    @DisplayName("PATCH body must be exactly the target state: boolean, no null, no other fields")
    void patchValidatesTheBody() throws Exception {
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field=='body' && @.issue=='empty_patch')]").exists());
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content(completed("null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='completed' && @.issue=='required')]").exists());
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content(completed("\"true\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='completed' && @.issue=='invalid_type')]").exists());
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content(completed("1")))
                .andExpect(status().isBadRequest());
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content("[true]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='body' && @.issue=='invalid_type')]").exists());
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].issue").value("malformed_json"));
        verify(service, never()).setCompleted(any(), any(), any(), anyBoolean());
    }

    @Test
    void patchRejectsOwnerFieldsEvenWhenEditingContent() throws Exception {
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"goalId\":\"x\",\"userId\":\"x\",\"title\":\"đổi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='goalId' && @.issue=='unknown_field')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='userId' && @.issue=='unknown_field')]").exists());
        verify(service, never()).setCompleted(any(), any(), any(), anyBoolean());
    }

    @Test
    void unknownOtherOwnersOrMalformedIdsAre404NotFound() throws Exception {
        when(service.setCompleted(USER, GOAL, TASK, true)).thenThrow(new TaskService.TaskNotFoundException());
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content(completed("true")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        when(service.setCompleted(USER, GOAL, TASK, false)).thenThrow(new GoalService.GoalNotFoundException());
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content(completed("false")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mvc.perform(asUser(patch("/api/v1/goals/not-a-uuid/tasks/" + TASK)).contentType(MediaType.APPLICATION_JSON)
                        .content(completed("true")))
                .andExpect(status().isNotFound());
        mvc.perform(asUser(get("/api/v1/goals/" + GOAL + "/tasks/not-a-uuid"))).andExpect(status().isNotFound());
    }

    @Test
    void detailReturnsTheTaskInEnvelope() throws Exception {
        when(service.getTask(USER, GOAL, TASK)).thenReturn(stored(false));
        mvc.perform(asUser(get(ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Ôn SQL"))
                .andExpect(jsonPath("$.data.estimatedMinutes").value(30));
    }

    @Test
    void listByDateNeedsAValidDateAndReturnsDataWithMeta() throws Exception {
        when(service.listByDate(USER, LocalDate.of(2026, 11, 10), 1, 20))
                .thenReturn(new TaskService.TaskPage(List.of(stored(true), stored(false)), 41));
        mvc.perform(asUser(get("/api/v1/tasks?date=2026-11-10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.totalItems").value(41))
                .andExpect(jsonPath("$.meta.totalPages").value(3));

        mvc.perform(asUser(get("/api/v1/tasks")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='date' && @.issue=='required')]").exists());
        mvc.perform(asUser(get("/api/v1/tasks?date=10/11/2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='date' && @.issue=='invalid_date')]").exists());
        mvc.perform(asUser(get("/api/v1/tasks?date=2026-11-10&limit=101"))).andExpect(status().isBadRequest());
        mvc.perform(asUser(get("/api/v1/tasks?date=2026-11-10&page=0"))).andExpect(status().isBadRequest());
    }

    @Test
    void emptyDayIsAnEmptyList() throws Exception {
        when(service.listByDate(USER, LocalDate.of(2026, 11, 11), 1, 20))
                .thenReturn(new TaskService.TaskPage(List.of(), 0));
        mvc.perform(asUser(get("/api/v1/tasks?date=2026-11-11")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalItems").value(0))
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void listByGoalAcceptsOptionalDateAndCompletedFilters() throws Exception {
        when(service.listByGoal(USER, GOAL, LocalDate.of(2026, 11, 10), false, 1, 20))
                .thenReturn(new TaskService.TaskPage(List.of(stored(false)), 1));
        mvc.perform(asUser(get("/api/v1/goals/" + GOAL + "/tasks?date=2026-11-10&completed=false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].completed").value(false));

        mvc.perform(asUser(get("/api/v1/goals/" + GOAL + "/tasks?completed=maybe")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='completed' && @.issue=='invalid_type')]").exists());

        when(service.listByGoal(USER, GOAL, null, null, 1, 20)).thenThrow(new GoalService.GoalNotFoundException());
        mvc.perform(asUser(get("/api/v1/goals/" + GOAL + "/tasks")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test void createDeleteAndDirectEditUseSharedEnvelopeAndStrictInputs() throws Exception {
        when(service.createTask(eq(USER), eq(GOAL), any())).thenReturn(stored(false));
        mvc.perform(asUser(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/goals/" + GOAL + "/tasks"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"SQL\",\"scheduledDate\":\"2026-11-10\",\"estimatedMinutes\":30}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(TASK.toString()));
        mvc.perform(asUser(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(ONE))).andExpect(status().isNoContent());
        when(service.updateTaskDirect(eq(USER), eq(TASK), any())).thenReturn(stored(false));
        mvc.perform(asUser(patch("/api/v1/tasks/" + TASK)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"New title\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(TASK.toString()));
        mvc.perform(asUser(patch(ONE)).contentType(MediaType.APPLICATION_JSON).content("{\"estimatedMinutes\":30.5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].issue").value("invalid_type"));
    }
}
