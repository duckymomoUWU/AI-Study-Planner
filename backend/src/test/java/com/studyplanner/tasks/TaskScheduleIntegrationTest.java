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
 * Bộ kiểm thử tích hợp (Integration Test) kiểm tra toàn bộ các quy tắc lập lịch,
 * ngân sách thời gian, bảo mật cô lập dữ liệu và tính nguyên tử theo đặc tả Backend API Spec.
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

    // User A & User B để kiểm thử Data Isolation
    private final UUID userAId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userBId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final UUID goalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID taskId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    // Mục tiêu học tập từ ngày 09/11/2026 đến ngày thi 12/11/2026 (ngân sách 60 phút/ngày)
    private final LocalDate startDate = LocalDate.of(2026, 11, 9);
    private final LocalDate examDate = LocalDate.of(2026, 11, 12);
    private final int minutesPerDay = 60;

    private AppUserPrincipal principalA;
    private AppUserPrincipal principalB;

    @BeforeEach
    void setUp() {
        principalA = new AppUserPrincipal(userAId, "studentA@example.com", "hashA");
        principalB = new AppUserPrincipal(userBId, "studentB@example.com", "hashB");

        // Cấu hình mock Goal thuộc quyền sở hữu của User A
        var storedGoal = new GoalJdbcRepository.StoredGoal(
                goalId, userAId, "Cơ sở dữ liệu", "SQL JOIN, Chuẩn hóa",
                startDate, examDate, minutesPerDay, "Asia/Ho_Chi_Minh", 0
        );

        when(goalRepository.lockOwned(userAId, goalId)).thenReturn(Optional.of(storedGoal));
        when(goalRepository.findOwned(userAId, goalId)).thenReturn(Optional.of(storedGoal));

        // User B không sở hữu goal này
        when(goalRepository.lockOwned(userBId, goalId)).thenReturn(Optional.empty());
        when(goalRepository.findOwned(userBId, goalId)).thenReturn(Optional.empty());
    }

    private TaskJdbcRepository.StoredTask createMockStoredTask(LocalDate date, int minutes, boolean completed) {
        return new TaskJdbcRepository.StoredTask(
                taskId, goalId, "Cơ sở dữ liệu", "Ôn tập SQL",
                date, minutes, completed, "manual", Instant.now()
        );
    }

    // =========================================================================
    // 1. testCreateTask_OnStartDate_Success: Xếp task vào đúng startDate -> 201
    // =========================================================================
    @Test
    @DisplayName("1. testCreateTask_OnStartDate_Success: Xếp task vào đúng ngày startDate -> 201 Created")
    void testCreateTask_OnStartDate_Success() throws Exception {
        LocalDate taskDate = startDate; // 2026-11-09
        int estimatedMinutes = 45;

        when(taskRepository.getDailyUsedMinutes(goalId, taskDate, null)).thenReturn(0);
        when(taskRepository.insertTask(eq(goalId), anyString(), eq(taskDate), eq(estimatedMinutes), eq("manual"), anyString()))
                .thenReturn(createMockStoredTask(taskDate, estimatedMinutes, false));

        var request = new CreateTaskRequest("Ôn mô hình quan hệ", taskDate, estimatedMinutes);

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

        verify(taskRepository).insertTask(eq(goalId), eq("Ôn mô hình quan hệ"), eq(taskDate), eq(45), eq("manual"), eq("Cơ sở dữ liệu"));
        verify(taskRepository).incrementGoalVersion(userAId, goalId);
    }

    // =========================================================================
    // 2. testCreateTask_OnDayBeforeExam_Success: Xếp task vào ngày trước thi -> 201
    // =========================================================================
    @Test
    @DisplayName("2. testCreateTask_OnDayBeforeExam_Success: Xếp task vào ngày liền kề trước examDate -> 201 Created")
    void testCreateTask_OnDayBeforeExam_Success() throws Exception {
        LocalDate dayBeforeExam = examDate.minusDays(1); // 2026-11-11
        int estimatedMinutes = 50;

        when(taskRepository.getDailyUsedMinutes(goalId, dayBeforeExam, null)).thenReturn(0);
        when(taskRepository.insertTask(eq(goalId), anyString(), eq(dayBeforeExam), eq(estimatedMinutes), eq("manual"), anyString()))
                .thenReturn(createMockStoredTask(dayBeforeExam, estimatedMinutes, false));

        var request = new CreateTaskRequest("Tổng ôn trước ngày thi", dayBeforeExam, estimatedMinutes);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduledDate").value(dayBeforeExam.toString()))
                .andExpect(jsonPath("$.estimatedMinutes").value(50));

        verify(taskRepository).insertTask(eq(goalId), eq("Tổng ôn trước ngày thi"), eq(dayBeforeExam), eq(50), eq("manual"), eq("Cơ sở dữ liệu"));
    }

    // =========================================================================
    // 3. testCreateTask_OnExamDate_ShouldFail: Cố tình xếp vào ngày thi -> 400
    // =========================================================================
    @Test
    @DisplayName("3. testCreateTask_OnExamDate_ShouldFail: Cố tình xếp task vào đúng ngày thi -> Mong đợi 400, DB không có task mới")
    void testCreateTask_OnExamDate_ShouldFail() throws Exception {
        LocalDate taskDate = examDate; // 2026-11-12 (ngày thi bị loại trừ)
        var request = new CreateTaskRequest("Ôn tập ngày thi", taskDate, 30);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("scheduledDate"))
                .andExpect(jsonPath("$.fieldErrors[0].issue").value("on_or_after_exam_date"));

        // Xác nhận tính nguyên tử: Tuyệt đối không insert vào cơ sở dữ liệu
        verify(taskRepository, never()).insertTask(any(), any(), any(), anyInt(), any(), any());
        verify(taskRepository, never()).incrementGoalVersion(any(), any());
    }

    // =========================================================================
    // 4. testCreateTask_ExceedDailyMinutes_ShouldFail: Vượt định mức -> 422
    // =========================================================================
    @Test
    @DisplayName("4. testCreateTask_ExceedDailyMinutes_ShouldFail: Đã có 45 phút, định mức 60, tạo thêm 30 phút -> 422 DAILY_BUDGET_EXCEEDED")
    void testCreateTask_ExceedDailyMinutes_ShouldFail() throws Exception {
        LocalDate taskDate = startDate.plusDays(1); // 2026-11-10
        // Ngày đã dùng 45 phút
        when(taskRepository.getDailyUsedMinutes(goalId, taskDate, null)).thenReturn(45);

        // Cố tình thêm task 30 phút (Tổng 45 + 30 = 75 phút > 60 phút định mức)
        var request = new CreateTaskRequest("Giải bài tập nâng cao", taskDate, 30);

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

        // Xác nhận rollback hoàn toàn: Không ghi nhận task
        verify(taskRepository, never()).insertTask(any(), any(), any(), anyInt(), any(), any());
    }

    // =========================================================================
    // 5. testCreateTask_DailyMinutesExactBudget_Success: Đúng bằng định mức -> 201
    // =========================================================================
    @Test
    @DisplayName("5. testCreateTask_DailyMinutesExactBudget_Success: Tổng đúng bằng định mức (30 + 30 = 60/60 phút) -> 201 Created")
    void testCreateTask_DailyMinutesExactBudget_Success() throws Exception {
        LocalDate taskDate = startDate.plusDays(1); // 2026-11-10
        // Đã có 30 phút, thêm đúng 30 phút -> Tổng vừa tròn 60 phút
        when(taskRepository.getDailyUsedMinutes(goalId, taskDate, null)).thenReturn(30);
        when(taskRepository.insertTask(eq(goalId), anyString(), eq(taskDate), eq(30), eq("manual"), anyString()))
                .thenReturn(createMockStoredTask(taskDate, 30, false));

        var request = new CreateTaskRequest("Học 30 phút cuối ngày", taskDate, 30);

        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedMinutes").value(30));

        verify(taskRepository).insertTask(eq(goalId), eq("Học 30 phút cuối ngày"), eq(taskDate), eq(30), eq("manual"), eq("Cơ sở dữ liệu"));
    }

    // =========================================================================
    // 6. testUpdateTask_ChangeDateOrMinutes_ValidateBudgetCorrectly: Dời ngày / sửa giờ
    // =========================================================================
    @Test
    @DisplayName("6. testUpdateTask_ChangeDateOrMinutes_ValidateBudgetCorrectly: Sửa task chuyển sang ngày khác, nhả ngân sách cũ")
    void testUpdateTask_ChangeDateOrMinutes_ValidateBudgetCorrectly() throws Exception {
        LocalDate oldDate = startDate; // 2026-11-09
        LocalDate newDate = startDate.plusDays(1); // 2026-11-10

        var existingTask = createMockStoredTask(oldDate, 40, false);
        when(taskRepository.findTaskInGoalOwned(userAId, goalId, taskId)).thenReturn(Optional.of(existingTask));

        // Tại ngày mới (2026-11-10), các task khác đã dùng 15 phút
        when(taskRepository.getDailyUsedMinutes(goalId, newDate, taskId)).thenReturn(15);

        // Chuyển task sang ngày mới với 45 phút (15 + 45 = 60 <= 60 -> Hợp lệ)
        when(taskRepository.updateTask(eq(taskId), eq(goalId), anyString(), eq(newDate), eq(45), eq(false)))
                .thenReturn(true);

        var updatedTask = createMockStoredTask(newDate, 45, false);
        when(taskRepository.findTaskInGoalOwned(userAId, goalId, taskId))
                .thenReturn(Optional.of(existingTask))
                .thenReturn(Optional.of(updatedTask));

        var updateRequest = new UpdateTaskRequest("Ôn SQL dời sang ngày 10", newDate, 45, false);

        mockMvc.perform(patch("/api/goals/{goalId}/tasks/{taskId}", goalId, taskId)
                        .with(user(principalA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDate").value(newDate.toString()))
                .andExpect(jsonPath("$.estimatedMinutes").value(45));

        verify(taskRepository).updateTask(eq(taskId), eq(goalId), eq("Ôn SQL dời sang ngày 10"), eq(newDate), eq(45), eq(false));
        verify(taskRepository).incrementGoalVersion(userAId, goalId);
    }

    // =========================================================================
    // 7. testDataIsolation_UserBCannotAccessUserATasks: User B gọi task của User A -> 404
    // =========================================================================
    @Test
    @DisplayName("7. testDataIsolation_UserBCannotAccessUserATasks: Dùng session của User B gọi task của User A -> 404 NOT_FOUND")
    void testDataIsolation_UserBCannotAccessUserATasks() throws Exception {
        // User B không có quyền trên goal hoặc task của User A
        when(taskRepository.findTaskInGoalOwned(userBId, goalId, taskId)).thenReturn(Optional.empty());
        when(taskRepository.findTaskOwned(userBId, taskId)).thenReturn(Optional.empty());

        // 1. User B cố đọc task của User A
        mockMvc.perform(get("/api/goals/{goalId}/tasks/{taskId}", goalId, taskId)
                        .with(user(principalB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 2. User B cố cập nhật task của User A
        var updateRequest = new UpdateTaskRequest("Hacker đổi tên", null, null, null);
        mockMvc.perform(patch("/api/goals/{goalId}/tasks/{taskId}", goalId, taskId)
                        .with(user(principalB))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 3. User B cố xóa task của User A
        mockMvc.perform(delete("/api/goals/{goalId}/tasks/{taskId}", goalId, taskId)
                        .with(user(principalB))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 4. User B cố tạo task vào goal của User A
        var createRequest = new CreateTaskRequest("Task trái phép", startDate, 30);
        mockMvc.perform(post("/api/goals/{goalId}/tasks", goalId)
                        .with(user(principalB))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Đảm bảo không có thao tác ghi nào được thực hiện
        verify(taskRepository, never()).updateTask(any(), any(), any(), any(), anyInt(), anyBoolean());
        verify(taskRepository, never()).deleteTask(any(), any());
    }

    // =========================================================================
    // 8. testCompletedTask_StillConsumesDailyBudget: Task hoàn thành vẫn giữ quota
    // =========================================================================
    @Test
    @DisplayName("8. Task đã hoàn thành (completed = true) vẫn chiếm ngân sách, không sinh thêm quota")
    void testCompletedTask_StillConsumesDailyBudget() throws Exception {
        LocalDate taskDate = startDate;
        // Ngày đã có 50 phút từ task đã hoàn thành (completed=true)
        when(taskRepository.getDailyUsedMinutes(goalId, taskDate, null)).thenReturn(50);

        // Tạo thêm task 20 phút (50 + 20 = 70 > 60) -> Phải bị từ chối
        var request = new CreateTaskRequest("Học thêm sau khi đã xong việc", taskDate, 20);

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
    // 9. testCreateTask_BeforeStartDate_ShouldFail: Trước startDate -> 400
    // =========================================================================
    @Test
    @DisplayName("9. Xếp task trước ngày bắt đầu startDate -> 400 VALIDATION_ERROR")
    void testCreateTask_BeforeStartDate_ShouldFail() throws Exception {
        LocalDate beforeStart = startDate.minusDays(1); // 2026-11-08
        var request = new CreateTaskRequest("Học sớm trước ngày bắt đầu", beforeStart, 30);

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
