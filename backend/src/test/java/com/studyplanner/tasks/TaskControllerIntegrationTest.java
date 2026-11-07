package com.studyplanner.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.auth.AppUser;
import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.auth.SecurityConfig;
import com.studyplanner.tasks.dto.CompleteTaskRequest;
import com.studyplanner.tasks.dto.CreateTaskRequest;
import com.studyplanner.tasks.dto.DailyTasksResponse;
import com.studyplanner.tasks.dto.GoalProgressResponse;
import com.studyplanner.tasks.dto.PaginationMeta;
import com.studyplanner.tasks.dto.SingleTaskResponse;
import com.studyplanner.tasks.dto.TaskPageResponse;
import com.studyplanner.tasks.dto.TaskResponse;
import com.studyplanner.tasks.dto.UpdateTaskRequest;

@WebMvcTest(controllers = TaskController.class)
@Import({SecurityConfig.class, TaskExceptionHandler.class})
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    @MockBean
    private UserDetailsService userDetailsService;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID goalId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID taskId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private AppUserPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new AppUserPrincipal(userId, "student@example.com", "secret");
    }

    private TaskResponse sampleTask() {
        return new TaskResponse(
                taskId, goalId, "CÆ¡ sá»Ÿ dá»¯ liá»‡u", "Ã”n SQL JOIN",
                LocalDate.of(2026, 11, 10), 45, false, "manual", Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/goals/{goalId}/tasks creates task and returns 201 with both top-level and data envelope")
    void createTaskSucceeds() throws Exception {
        var req = new CreateTaskRequest("Ã”n SQL JOIN", LocalDate.of(2026, 11, 10), 45);
        when(taskService.createTask(eq(userId), eq(goalId), any(CreateTaskRequest.class)))
                .thenReturn(SingleTaskResponse.of(sampleTask()));

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.title").value("Ã”n SQL JOIN"))
                .andExpect(jsonPath("$.estimatedMinutes").value(45))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.data.id").value(taskId.toString()))
                .andExpect(jsonPath("$.data.title").value("Ã”n SQL JOIN"));
    }

    @Test
    @DisplayName("POST /api/goals/{goalId}/tasks returns 422 DAILY_BUDGET_EXCEEDED when daily minutes exceed budget")
    void createTaskDailyBudgetExceeded() throws Exception {
        var req = new CreateTaskRequest("Ã”n SQL JOIN", LocalDate.of(2026, 11, 10), 90);
        when(taskService.createTask(eq(userId), eq(goalId), any(CreateTaskRequest.class)))
                .thenThrow(new DailyBudgetExceededException(LocalDate.of(2026, 11, 10), 60, 90, 30));

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.error.code").value("DAILY_BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.error.details[0].budgetMinutes").value(60))
                .andExpect(jsonPath("$.error.details[0].excessMinutes").value(30));
    }

    @Test
    @DisplayName("GET /api/tasks?date=YYYY-MM-DD returns daily tasks with summary counts and items")
    void getDailyTasksSucceeds() throws Exception {
        var response = DailyTasksResponse.of("2026-11-10", List.of(sampleTask()), PaginationMeta.of(1, 20, 1));
        when(taskService.listTasksByDate(eq(userId), eq(LocalDate.of(2026, 11, 10)), anyInt(), anyInt()))
                .thenReturn(response);

        mockMvc.perform(get("/api/tasks")
                        .param("date", "2026-11-10")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-10"))
                .andExpect(jsonPath("$.totalEstimatedMinutes").value(45))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(taskId.toString()))
                .andExpect(jsonPath("$.data[0].id").value(taskId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/tasks/{taskId}/complete toggles task completion status")
    void patchTaskCompletion() throws Exception {
        var completedTask = new TaskResponse(
                taskId, goalId, "CÆ¡ sá»Ÿ dá»¯ liá»‡u", "Ã”n SQL JOIN",
                LocalDate.of(2026, 11, 10), 45, true, "manual", Instant.now()
        );
        when(taskService.setTaskCompletion(eq(userId), eq(taskId), eq(true)))
                .thenReturn(SingleTaskResponse.of(completedTask));

        mockMvc.perform(patch("/api/tasks/{taskId}/complete", taskId)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompleteTaskRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    @DisplayName("DELETE /api/tasks/{taskId} deletes task and returns 204")
    void deleteTaskDirectSucceeds() throws Exception {
        doNothing().when(taskService).deleteTaskDirect(userId, taskId);

        mockMvc.perform(delete("/api/tasks/{taskId}", taskId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/goals/{goalId}/progress returns progress rate and task counts")
    void getGoalProgressSucceeds() throws Exception {
        when(taskService.getGoalProgress(userId, goalId))
                .thenReturn(GoalProgressResponse.of(goalId, 4, 3));

        mockMvc.perform(get("/api/goals/{goalId}/progress", goalId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(goalId.toString()))
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.completedTasks").value(3))
                .andExpect(jsonPath("$.remainingTasks").value(1))
                .andExpect(jsonPath("$.completionRate").value(0.75))
                .andExpect(jsonPath("$.data.completionRate").value(0.75));
    }

    @Test
    @DisplayName("GET /api/tasks/overdue returns overdue tasks")
    void getOverdueTasksSucceeds() throws Exception {
        when(taskService.listOverdueTasks(userId)).thenReturn(List.of(sampleTask()));

        mockMvc.perform(get("/api/tasks/overdue")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(taskId.toString()));
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticatedRequestFails() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }
}
