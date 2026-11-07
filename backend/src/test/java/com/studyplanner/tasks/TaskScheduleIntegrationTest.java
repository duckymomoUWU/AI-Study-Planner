package com.studyplanner.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.util.Optional;
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
import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.auth.SecurityConfig;
import com.studyplanner.goals.GoalJdbcRepository;
import com.studyplanner.goals.GoalTimeConfiguration;
import com.studyplanner.tasks.dto.CreateTaskRequest;
import com.studyplanner.tasks.dto.UpdateTaskRequest;

/**
 * Bá»™ kiá»ƒm thá»­ tÃ­ch há»£p (Integration Test) kiá»ƒm tra toÃ n bá»™ cÃ¡c quy táº¯c láº­p lá»‹ch,
 * ngÃ¢n sÃ¡ch thá»i gian, báº£o máº­t cÃ´ láº­p dá»¯ liá»‡u vÃ  tÃ­nh nguyÃªn tá»­ theo Ä‘áº·c táº£ Backend API Spec.
 */
@WebMvcTest(controllers = TaskController.class)
@Import({TaskService.class, SecurityConfig.class, TaskExceptionHandler.class, GoalTimeConfiguration.class})
class TaskScheduleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskJdbcRepository taskRepository;

    @MockBean
    private GoalJdbcRepository goalRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    // User A & User B Ä‘á»ƒ kiá»ƒm thá»­ Data Isolation
    private final UUID userAId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userBId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final UUID goalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID taskId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    // Má»¥c tiÃªu há»c táº­p tá»« ngÃ y 09/11/2026 Ä‘áº¿n ngÃ y thi 12/11/2026 (ngÃ¢n sÃ¡ch 60 phÃºt/ngÃ y)
    private final LocalDate startDate = LocalDate.of(2026, 11, 9);
    private final LocalDate examDate = LocalDate.of(2026, 11, 12);
    private final int minutesPerDay = 60;

    private AppUserPrincipal principalA;
    private AppUserPrincipal principalB;

    @BeforeEach
    void setUp() {
        principalA = new AppUserPrincipal(userAId, "studentA@example.com", "hashA");
        principalB = new AppUserPrincipal(userBId, "studentB@example.com", "hashB");

        // Cáº¥u hÃ¬nh mock Goal thuá»™c quyá»n sá»Ÿ há»¯u cá»§a User A
        var storedGoal = new GoalJdbcRepository.StoredGoal(
                goalId, userAId, "CÆ¡ sá»Ÿ dá»¯ liá»‡u", "SQL JOIN, Chuáº©n hÃ³a",
                startDate, examDate, minutesPerDay, "Asia/Ho_Chi_Minh", 0
        );

        when(goalRepository.lockOwned(userAId, goalId)).thenReturn(Optional.of(storedGoal));
        when(goalRepository.findOwned(userAId, goalId)).thenReturn(Optional.of(storedGoal));

        // User B khÃ´ng sá»Ÿ há»¯u goal nÃ y
        when(goalRepository.lockOwned(userBId, goalId)).thenReturn(Optional.empty());
        when(goalRepository.findOwned(userBId, goalId)).thenReturn(Optional.empty());
    }

    private TaskJdbcRepository.StoredTask createMockStoredTask(LocalDate date, int minutes, boolean completed) {
        return new TaskJdbcRepository.StoredTask(
                taskId, goalId, "CÆ¡ sá»Ÿ dá»¯ liá»‡u", "Ã”n táº­p SQL",
                date, minutes, completed, "manual", Instant.now()
        );
    }

    // =========================================================================
    // 1. testCreateTask_OnStartDate_Success: Xáº¿p task vÃ o Ä‘Ãºng startDate -> 201
    // =========================================================================
    @Test
    @DisplayName("1. testCreateTask_OnStartDate_Success: Xáº¿p task vÃ o Ä‘Ãºng ngÃ y startDate -> 201 Created")
    void testCreateTask_OnStartDate_Success() throws Exception {
        LocalDate taskDate = startDate; // 2026-11-09
        int estimatedMinutes = 45;

        when(taskRepository.getDailyUsedMinutes(goalId, taskDate, null)).thenReturn(0);
        when(taskRepository.insertTask(eq(goalId), anyString(), eq(taskDate), eq(estimatedMinutes), eq("manual"), anyString()))
                .thenReturn(createMockStoredTask(taskDate, estimatedMinutes, false));

        var request = new CreateTaskRequest("Ã”n mÃ´ hÃ¬nh quan há»‡", taskDate, estimatedMinutes);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.scheduledDate").value(taskDate.toString()))
                .andExpect(jsonPath("$.estimatedMinutes").value(45))
                .andExpect(jsonPath("$.completed").value(false));

        verify(taskRepository).insertTask(eq(goalId), eq("Ã”n mÃ´ hÃ¬nh quan há»‡"), eq(taskDate), eq(45), eq("manual"), eq("CÆ¡ sá»Ÿ dá»¯ liá»‡u"));
        verify(taskRepository).incrementGoalVersion(userAId, goalId);
    }

    // =========================================================================
    // 2. testCreateTask_OnDayBeforeExam_Success: Xáº¿p task vÃ o ngÃ y trÆ°á»›c thi -> 201
    // =========================================================================
    @Test
    @DisplayName("2. testCreateTask_OnDayBeforeExam_Success: Xáº¿p task vÃ o ngÃ y liá»n ká» trÆ°á»›c examDate -> 201 Created")
    void testCreateTask_OnDayBeforeExam_Success() throws Exception {
        LocalDate dayBeforeExam = examDate.minusDays(1); // 2026-11-11
        int estimatedMinutes = 50;

        when(taskRepository.getDailyUsedMinutes(goalId, dayBeforeExam, null)).thenReturn(0);
        when(taskRepository.insertTask(eq(goalId), anyString(), eq(dayBeforeExam), eq(estimatedMinutes), eq("manual"), anyString()))
                .thenReturn(createMockStoredTask(dayBeforeExam, estimatedMinutes, false));

        var request = new CreateTaskRequest("Tá»•ng Ã´n trÆ°á»›c ngÃ y thi", dayBeforeExam, estimatedMinutes);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduledDate").value(dayBeforeExam.toString()))
                .andExpect(jsonPath("$.estimatedMinutes").value(50));

        verify(taskRepository).insertTask(eq(goalId), eq("Tá»•ng Ã´n trÆ°á»›c ngÃ y thi"), eq(dayBeforeExam), eq(50), eq("manual"), eq("CÆ¡ sá»Ÿ dá»¯ liá»‡u"));
    }

    // =========================================================================
    // 3. testCreateTask_OnExamDate_ShouldFail: Cá»‘ tÃ¬nh xáº¿p vÃ o ngÃ y thi -> 400
    // =========================================================================
    @Test
    @DisplayName("3. testCreateTask_OnExamDate_ShouldFail: Cá»‘ tÃ¬nh xáº¿p task vÃ o Ä‘Ãºng ngÃ y thi -> Mong Ä‘á»£i 400, DB khÃ´ng cÃ³ task má»›i")
    void testCreateTask_OnExamDate_ShouldFail() throws Exception {
        LocalDate taskDate = examDate; // 2026-11-12 (ngÃ y thi bá»‹ loáº¡i trá»«)
        var request = new CreateTaskRequest("Ã”n táº­p ngÃ y thi", taskDate, 30);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("scheduledDate"))
                .andExpect(jsonPath("$.fieldErrors[0].issue").value("on_or_after_exam_date"));

        // XÃ¡c nháº­n tÃ­nh nguyÃªn tá»­: Tuyá»‡t Ä‘á»‘i khÃ´ng insert vÃ o cÆ¡ sá»Ÿ dá»¯ liá»‡u
        verify(taskRepository, never()).insertTask(any(), any(), any(), anyInt(), any(), any());
        verify(taskRepository, never()).incrementGoalVersion(any(), any());
    }

    // =========================================================================
    // 4. testCreateTask_ExceedDailyMinutes_ShouldFail: VÆ°á»£t Ä‘á»‹nh má»©c -> 422
    // =========================================================================
    @Test
    @DisplayName("4. testCreateTask_ExceedDailyMinutes_ShouldFail: ÄÃ£ cÃ³ 45 phÃºt, Ä‘á»‹nh má»©c 60, táº¡o thÃªm 30 phÃºt -> 422 DAILY_BUDGET_EXCEEDED")
    void testCreateTask_ExceedDailyMinutes_ShouldFail() throws Exception {
        LocalDate taskDate = startDate.plusDays(1); // 2026-11-10
        // NgÃ y Ä‘Ã£ dÃ¹ng 45 phÃºt
        when(taskRepository.getDailyUsedMinutes(goalId, taskDate, null)).thenReturn(45);

        // Cá»‘ tÃ¬nh thÃªm task 30 phÃºt (Tá»•ng 45 + 30 = 75 phÃºt > 60 phÃºt Ä‘á»‹nh má»©c)
        var request = new CreateTaskRequest("Giáº£i bÃ i táº­p nÃ¢ng cao", taskDate, 30);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.error.code").value("DAILY_BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.error.details[0].scheduledDate").value(taskDate.toString()))
                .andExpect(jsonPath("$.error.details[0].budgetMinutes").value(60))
                .andExpect(jsonPath("$.error.details[0].scheduledMinutes").value(75))
                .andExpect(jsonPath("$.error.details[0].excessMinutes").value(15));

        // XÃ¡c nháº­n rollback hoÃ n toÃ n: KhÃ´ng ghi nháº­n task
        verify(taskRepository, never()).insertTask(any(), any(), any(), anyInt(), any(), any());
    }

    // =========================================================================
    // 5. testCreateTask_DailyMinutesExactBudget_Success: ÄÃºng báº±ng Ä‘á»‹nh má»©c -> 201
    // =========================================================================
    @Test
    @DisplayName("5. testCreateTask_DailyMinutesExactBudget_Success: Tá»•ng Ä‘Ãºng báº±ng Ä‘á»‹nh má»©c (30 + 30 = 60/60 phÃºt) -> 201 Created")
    void testCreateTask_DailyMinutesExactBudget_Success() throws Exception {
        LocalDate taskDate = startDate.plusDays(1); // 2026-11-10
        // ÄÃ£ cÃ³ 30 phÃºt, thÃªm Ä‘Ãºng 30 phÃºt -> Tá»•ng vá»«a trÃ²n 60 phÃºt
        when(taskRepository.getDailyUsedMinutes(goalId, taskDate, null)).thenReturn(30);
        when(taskRepository.insertTask(eq(goalId), anyString(), eq(taskDate), eq(30), eq("manual"), anyString()))
                .thenReturn(createMockStoredTask(taskDate, 30, false));

        var request = new CreateTaskRequest("Há»c 30 phÃºt cuá»‘i ngÃ y", taskDate, 30);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedMinutes").value(30));

        verify(taskRepository).insertTask(eq(goalId), eq("Há»c 30 phÃºt cuá»‘i ngÃ y"), eq(taskDate), eq(30), eq("manual"), eq("CÆ¡ sá»Ÿ dá»¯ liá»‡u"));
    }

    // =========================================================================
    // 6. testUpdateTask_ChangeDateOrMinutes_ValidateBudgetCorrectly: Dá»i ngÃ y / sá»­a giá»
    // =========================================================================
    @Test
    @DisplayName("6. testUpdateTask_ChangeDateOrMinutes_ValidateBudgetCorrectly: Sá»­a task chuyá»ƒn sang ngÃ y khÃ¡c, nháº£ ngÃ¢n sÃ¡ch cÅ©")
    void testUpdateTask_ChangeDateOrMinutes_ValidateBudgetCorrectly() throws Exception {
        LocalDate oldDate = startDate; // 2026-11-09
        LocalDate newDate = startDate.plusDays(1); // 2026-11-10

        var existingTask = createMockStoredTask(oldDate, 40, false);
        when(taskRepository.findTaskInGoalOwned(userAId, goalId, taskId)).thenReturn(Optional.of(existingTask));

        // Táº¡i ngÃ y má»›i (2026-11-10), cÃ¡c task khÃ¡c Ä‘Ã£ dÃ¹ng 15 phÃºt
        when(taskRepository.getDailyUsedMinutes(goalId, newDate, taskId)).thenReturn(15);

        // Chuyá»ƒn task sang ngÃ y má»›i vá»›i 45 phÃºt (15 + 45 = 60 <= 60 -> Há»£p lá»‡)
        when(taskRepository.updateTask(eq(taskId), eq(goalId), anyString(), eq(newDate), eq(45), eq(false)))
                .thenReturn(true);

        var updatedTask = createMockStoredTask(newDate, 45, false);
        when(taskRepository.findTaskInGoalOwned(userAId, goalId, taskId))
                .thenReturn(Optional.of(existingTask))
                .thenReturn(Optional.of(updatedTask));

        var updateRequest = new UpdateTaskRequest("Ã”n SQL dá»i sang ngÃ y 10", newDate, 45, false);

        mockMvc.perform(patch("/api/goals/{goalId}/tasks/{taskId}", goalId, taskId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDate").value(newDate.toString()))
                .andExpect(jsonPath("$.estimatedMinutes").value(45));

        verify(taskRepository).updateTask(eq(taskId), eq(goalId), eq("Ã”n SQL dá»i sang ngÃ y 10"), eq(newDate), eq(45), eq(false));
        verify(taskRepository).incrementGoalVersion(userAId, goalId);
    }

    // =========================================================================
    // 7. testDataIsolation_UserBCannotAccessUserATasks: User B gá»i task cá»§a User A -> 404
    // =========================================================================
    @Test
    @DisplayName("7. testDataIsolation_UserBCannotAccessUserATasks: DÃ¹ng session cá»§a User B gá»i task cá»§a User A -> 404 NOT_FOUND")
    void testDataIsolation_UserBCannotAccessUserATasks() throws Exception {
        // User B khÃ´ng cÃ³ quyá»n trÃªn goal hoáº·c task cá»§a User A
        when(taskRepository.findTaskInGoalOwned(userBId, goalId, taskId)).thenReturn(Optional.empty());
        when(taskRepository.findTaskOwned(userBId, taskId)).thenReturn(Optional.empty());

        // 1. User B cá»‘ Ä‘á»c task cá»§a User A
        mockMvc.perform(get("/api/goals/{goalId}/tasks/{taskId}", goalId, taskId)
                        .with(user(principalB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 2. User B cá»‘ cáº­p nháº­t task cá»§a User A
        var updateRequest = new UpdateTaskRequest("Hacker Ä‘á»•i tÃªn", null, null, null);
        mockMvc.perform(patch("/api/goals/{goalId}/tasks/{taskId}", goalId, taskId)
                        .with(user(principalB))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 3. User B cá»‘ xÃ³a task cá»§a User A
        mockMvc.perform(delete("/api/goals/{goalId}/tasks/{taskId}", goalId, taskId)
                        .with(user(principalB))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 4. User B cá»‘ táº¡o task vÃ o goal cá»§a User A
        var createRequest = new CreateTaskRequest("Task trÃ¡i phÃ©p", startDate, 30);
        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalB))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Äáº£m báº£o khÃ´ng cÃ³ thao tÃ¡c ghi nÃ o Ä‘Æ°á»£c thá»±c hiá»‡n
        verify(taskRepository, never()).updateTask(any(), any(), any(), any(), anyInt(), anyBoolean());
        verify(taskRepository, never()).deleteTask(any(), any());
    }

    // =========================================================================
    // 8. testCompletedTask_StillConsumesDailyBudget: Task hoÃ n thÃ nh váº«n giá»¯ quota
    // =========================================================================
    @Test
    @DisplayName("8. Task Ä‘Ã£ hoÃ n thÃ nh (completed = true) váº«n chiáº¿m ngÃ¢n sÃ¡ch, khÃ´ng sinh thÃªm quota")
    void testCompletedTask_StillConsumesDailyBudget() throws Exception {
        LocalDate taskDate = startDate;
        // NgÃ y Ä‘Ã£ cÃ³ 50 phÃºt tá»« task Ä‘Ã£ hoÃ n thÃ nh (completed=true)
        when(taskRepository.getDailyUsedMinutes(goalId, taskDate, null)).thenReturn(50);

        // Táº¡o thÃªm task 20 phÃºt (50 + 20 = 70 > 60) -> Pháº£i bá»‹ tá»« chá»‘i
        var request = new CreateTaskRequest("Há»c thÃªm sau khi Ä‘Ã£ xong viá»‡c", taskDate, 20);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_BUDGET_EXCEEDED"));

        verify(taskRepository, never()).insertTask(any(), any(), any(), anyInt(), any(), any());
    }

    // =========================================================================
    // 9. testCreateTask_BeforeStartDate_ShouldFail: TrÆ°á»›c startDate -> 400
    // =========================================================================
    @Test
    @DisplayName("9. Xáº¿p task trÆ°á»›c ngÃ y báº¯t Ä‘áº§u startDate -> 400 VALIDATION_ERROR")
    void testCreateTask_BeforeStartDate_ShouldFail() throws Exception {
        LocalDate beforeStart = startDate.minusDays(1); // 2026-11-08
        var request = new CreateTaskRequest("Há»c sá»›m trÆ°á»›c ngÃ y báº¯t Ä‘áº§u", beforeStart, 30);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].issue").value("before_start_date"));

        verify(taskRepository, never()).insertTask(any(), any(), any(), anyInt(), any(), any());
    }
}
