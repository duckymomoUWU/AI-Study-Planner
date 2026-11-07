package com.studyplanner.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.tasks.dto.CompleteTaskRequest;
import com.studyplanner.tasks.dto.CreateTaskRequest;
import com.studyplanner.tasks.dto.DailyTasksResponse;
import com.studyplanner.tasks.dto.GoalProgressResponse;
import com.studyplanner.tasks.dto.SingleTaskResponse;
import com.studyplanner.tasks.dto.TaskErrorResponse;
import com.studyplanner.tasks.dto.TaskPageResponse;
import com.studyplanner.tasks.dto.TaskResponse;
import com.studyplanner.tasks.dto.UpdateTaskRequest;

import jakarta.validation.Valid;

@RestController
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = Objects.requireNonNull(taskService, "taskService");
    }

    private static UUID getUserId(AppUserPrincipal principal) {
        if (principal == null) {
            throw new TaskNotFoundException("Chưa đăng nhập.");
        }
        return principal.id();
    }

    // --- Create Task ---
    @PostMapping({"/api/goals/{goalId}/tasks", "/api/v1/goals/{goalId}/tasks"})
    public ResponseEntity<?> createTask(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId,
            @Valid @RequestBody CreateTaskRequest request) {
        if (principal == null) {
            return unauthenticated();
        }
        SingleTaskResponse created = taskService.createTask(principal.id(), goalId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // --- List Tasks in Goal ---
    @GetMapping({"/api/goals/{goalId}/tasks", "/api/v1/goals/{goalId}/tasks"})
    public ResponseEntity<?> listGoalTasks(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        if (principal == null) {
            return unauthenticated();
        }
        TaskPageResponse response = taskService.listTasksInGoal(principal.id(), goalId, date, completed, page, limit);
        return ResponseEntity.ok(response);
    }

    // --- Get Task Details ---
    @GetMapping({"/api/goals/{goalId}/tasks/{taskId}", "/api/v1/goals/{goalId}/tasks/{taskId}"})
    public ResponseEntity<?> getTask(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId,
            @PathVariable UUID taskId) {
        if (principal == null) {
            return unauthenticated();
        }
        SingleTaskResponse task = taskService.getTask(principal.id(), goalId, taskId);
        return ResponseEntity.ok(task);
    }

    // --- Update Task ---
    @PatchMapping({"/api/goals/{goalId}/tasks/{taskId}", "/api/v1/goals/{goalId}/tasks/{taskId}"})
    public ResponseEntity<?> updateTask(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        if (principal == null) {
            return unauthenticated();
        }
        SingleTaskResponse updated = taskService.updateTask(principal.id(), goalId, taskId, request);
        return ResponseEntity.ok(updated);
    }

    // --- Delete Task from Goal ---
    @DeleteMapping({"/api/goals/{goalId}/tasks/{taskId}", "/api/v1/goals/{goalId}/tasks/{taskId}"})
    public ResponseEntity<?> deleteTask(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId,
            @PathVariable UUID taskId) {
        if (principal == null) {
            return unauthenticated();
        }
        taskService.deleteTask(principal.id(), goalId, taskId);
        return ResponseEntity.noContent().build();
    }

    // --- Daily Tasks across goals (Lịch học theo ngày) ---
    @GetMapping({"/api/tasks", "/api/v1/tasks"})
    public ResponseEntity<?> listTasksByDate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit) {
        if (principal == null) {
            return unauthenticated();
        }
        DailyTasksResponse response = taskService.listTasksByDate(principal.id(), date, page, limit);
        return ResponseEntity.ok(response);
    }

    // --- Toggle / Complete Task (Convenience route used by frontend) ---
    @PatchMapping({"/api/tasks/{taskId}/complete", "/api/v1/tasks/{taskId}/complete"})
    public ResponseEntity<?> setTaskComplete(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID taskId,
            @Valid @RequestBody CompleteTaskRequest request) {
        if (principal == null) {
            return unauthenticated();
        }
        SingleTaskResponse updated = taskService.setTaskCompletion(principal.id(), taskId, request.completed());
        return ResponseEntity.ok(updated);
    }

    // --- Direct Update Task (Convenience route used by frontend) ---
    @PatchMapping({"/api/tasks/{taskId}", "/api/v1/tasks/{taskId}"})
    public ResponseEntity<?> updateTaskDirect(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        if (principal == null) {
            return unauthenticated();
        }
        SingleTaskResponse updated = taskService.updateTaskDirect(principal.id(), taskId, request);
        return ResponseEntity.ok(updated);
    }

    // --- Direct Delete Task (Convenience route used by frontend) ---
    @DeleteMapping({"/api/tasks/{taskId}", "/api/v1/tasks/{taskId}"})
    public ResponseEntity<?> deleteTaskDirect(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID taskId) {
        if (principal == null) {
            return unauthenticated();
        }
        taskService.deleteTaskDirect(principal.id(), taskId);
        return ResponseEntity.noContent().build();
    }

    // --- Overdue Tasks ---
    @GetMapping({"/api/tasks/overdue", "/api/v1/tasks/overdue"})
    public ResponseEntity<?> listOverdueTasks(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return unauthenticated();
        }
        List<TaskResponse> overdue = taskService.listOverdueTasks(principal.id());
        return ResponseEntity.ok(Map.of(
                "items", overdue,
                "data", overdue
        ));
    }

    // --- Goal Progress ---
    @GetMapping({"/api/goals/{goalId}/progress", "/api/v1/goals/{goalId}/progress"})
    public ResponseEntity<?> getGoalProgress(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId) {
        if (principal == null) {
            return unauthenticated();
        }
        GoalProgressResponse progress = taskService.getGoalProgress(principal.id(), goalId);
        return ResponseEntity.ok(progress);
    }

    private static ResponseEntity<TaskErrorResponse> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(TaskErrorResponse.of("UNAUTHORIZED", "Bạn cần đăng nhập."));
    }
}
