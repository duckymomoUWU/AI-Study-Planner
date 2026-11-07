package com.studyplanner.tasks;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyplanner.goals.GoalJdbcRepository;
import com.studyplanner.tasks.dto.CreateTaskRequest;
import com.studyplanner.tasks.dto.DailyTasksResponse;
import com.studyplanner.tasks.dto.GoalProgressResponse;
import com.studyplanner.tasks.dto.PaginationMeta;
import com.studyplanner.tasks.dto.SingleTaskResponse;
import com.studyplanner.tasks.dto.TaskPageResponse;
import com.studyplanner.tasks.dto.TaskResponse;
import com.studyplanner.tasks.dto.UpdateTaskRequest;

@Service
public class TaskService {
    private final TaskJdbcRepository taskRepository;
    private final GoalJdbcRepository goalRepository;
    private final Clock clock;
    private final ZoneId zoneId;

    public TaskService(TaskJdbcRepository taskRepository,
                       GoalJdbcRepository goalRepository,
                       Clock clock,
                       @Value("${app.default-timezone:Asia/Ho_Chi_Minh}") String timezone) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.goalRepository = Objects.requireNonNull(goalRepository, "goalRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zoneId = ZoneId.of(Objects.requireNonNull(timezone, "timezone"));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zoneId));
    }

    @Transactional
    public SingleTaskResponse createTask(UUID userId, UUID goalId, CreateTaskRequest request) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        // Validate basic fields
        TaskPolicy.TaskInput input = new TaskPolicy.TaskInput(
                request.title() != null ? request.title().trim() : null,
                request.scheduledDate(),
                request.estimatedMinutes()
        );
        List<TaskValidationException.FieldViolation> violations = new ArrayList<>(TaskPolicy.validateInput(input));
        if (!violations.isEmpty()) {
            throw new TaskValidationException(violations);
        }

        // Lock goal row to protect against concurrent budget races and verify ownership
        GoalJdbcRepository.StoredGoal goal = goalRepository.lockOwned(userId, goalId)
                .orElseThrow(() -> new TaskNotFoundException("Không tìm thấy mục tiêu hoặc bạn không có quyền truy cập."));

        // Validate task date within goal bounds: startDate <= scheduledDate < examDate
        violations.addAll(TaskPolicy.validateWithinGoal(request.scheduledDate(), goal.startDate(), goal.examDate()));
        if (!violations.isEmpty()) {
            throw new TaskValidationException(violations);
        }

        // Validate daily budget
        int currentUsed = taskRepository.getDailyUsedMinutes(goalId, request.scheduledDate(), null);
        TaskPolicy.checkDailyBudget(request.scheduledDate(), currentUsed, request.estimatedMinutes(), goal.minutesPerDay());

        // Insert task
        TaskJdbcRepository.StoredTask stored = taskRepository.insertTask(
                goalId,
                request.title().trim(),
                request.scheduledDate(),
                request.estimatedMinutes(),
                "manual",
                goal.subject()
        );

        // Increment goal version in the same transaction
        taskRepository.incrementGoalVersion(userId, goalId);

        return SingleTaskResponse.of(stored.toResponse());
    }

    @Transactional(readOnly = true)
    public SingleTaskResponse getTask(UUID userId, UUID goalId, UUID taskId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");

        TaskJdbcRepository.StoredTask stored = taskRepository.findTaskInGoalOwned(userId, goalId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        return SingleTaskResponse.of(stored.toResponse());
    }

    @Transactional(readOnly = true)
    public TaskPageResponse listTasksInGoal(UUID userId, UUID goalId, LocalDate date, Boolean completed, int page, int limit) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");

        // Verify goal ownership
        goalRepository.findOwned(userId, goalId)
                .orElseThrow(() -> new TaskNotFoundException("Không tìm thấy mục tiêu hoặc bạn không có quyền truy cập."));

        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, Math.min(100, limit));
        int offset = (safePage - 1) * safeLimit;

        List<TaskResponse> tasks = taskRepository.listTasksInGoal(userId, goalId, date, completed, offset, safeLimit)
                .stream().map(TaskJdbcRepository.StoredTask::toResponse).toList();
        long total = taskRepository.countTasksInGoal(userId, goalId, date, completed);

        return TaskPageResponse.of(tasks, PaginationMeta.of(safePage, safeLimit, total));
    }

    @Transactional
    public SingleTaskResponse updateTask(UUID userId, UUID goalId, UUID taskId, UpdateTaskRequest request) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (!request.hasAnyUpdate()) {
            throw new TaskValidationException("task", "empty_update", "Phải cung cấp ít nhất một trường cần cập nhật.");
        }

        // Lock goal row
        GoalJdbcRepository.StoredGoal goal = goalRepository.lockOwned(userId, goalId)
                .orElseThrow(() -> new TaskNotFoundException("Không tìm thấy mục tiêu hoặc bạn không có quyền truy cập."));

        TaskJdbcRepository.StoredTask existing = taskRepository.findTaskInGoalOwned(userId, goalId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        String targetTitle = request.title() != null ? request.title().trim() : existing.title();
        LocalDate targetDate = request.scheduledDate() != null ? request.scheduledDate() : existing.scheduledDate();
        int targetMinutes = request.estimatedMinutes() != null ? request.estimatedMinutes() : existing.estimatedMinutes();
        boolean targetCompleted = request.completed() != null ? request.completed() : existing.completed();

        // Validate merged values
        TaskPolicy.TaskInput input = new TaskPolicy.TaskInput(targetTitle, targetDate, targetMinutes);
        List<TaskValidationException.FieldViolation> violations = new ArrayList<>(TaskPolicy.validateInput(input));
        violations.addAll(TaskPolicy.validateWithinGoal(targetDate, goal.startDate(), goal.examDate()));
        if (!violations.isEmpty()) {
            throw new TaskValidationException(violations);
        }

        // If date or estimated minutes changed, validate daily budget (subtracting current task's old minutes)
        if (!targetDate.equals(existing.scheduledDate()) || targetMinutes != existing.estimatedMinutes()) {
            int usedOther = taskRepository.getDailyUsedMinutes(goalId, targetDate, taskId);
            TaskPolicy.checkDailyBudget(targetDate, usedOther, targetMinutes, goal.minutesPerDay());
        }

        taskRepository.updateTask(taskId, goalId, targetTitle, targetDate, targetMinutes, targetCompleted);

        // Versioning rule: real mutations bump version.
        // Setting completed to the same value does NOT bump version.
        boolean realChange = !targetTitle.equals(existing.title())
                || !targetDate.equals(existing.scheduledDate())
                || targetMinutes != existing.estimatedMinutes()
                || targetCompleted != existing.completed();

        if (realChange) {
            taskRepository.incrementGoalVersion(userId, goalId);
        }

        TaskJdbcRepository.StoredTask updated = taskRepository.findTaskInGoalOwned(userId, goalId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        return SingleTaskResponse.of(updated.toResponse());
    }

    @Transactional
    public SingleTaskResponse updateTaskDirect(UUID userId, UUID taskId, UpdateTaskRequest request) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        TaskJdbcRepository.StoredTask existing = taskRepository.findTaskOwned(userId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        return updateTask(userId, existing.goalId(), taskId, request);
    }

    @Transactional
    public SingleTaskResponse setTaskCompletion(UUID userId, UUID taskId, boolean completed) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");

        TaskJdbcRepository.StoredTask existing = taskRepository.findTaskOwned(userId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        // Lock parent goal
        goalRepository.lockOwned(userId, existing.goalId())
                .orElseThrow(TaskNotFoundException::new);

        if (completed != existing.completed()) {
            taskRepository.updateTask(
                    taskId,
                    existing.goalId(),
                    existing.title(),
                    existing.scheduledDate(),
                    existing.estimatedMinutes(),
                    completed
            );
            taskRepository.incrementGoalVersion(userId, existing.goalId());
        }

        TaskJdbcRepository.StoredTask updated = taskRepository.findTaskOwned(userId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        return SingleTaskResponse.of(updated.toResponse());
    }

    @Transactional
    public void deleteTask(UUID userId, UUID goalId, UUID taskId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");

        // Lock goal row
        goalRepository.lockOwned(userId, goalId)
                .orElseThrow(() -> new TaskNotFoundException("Không tìm thấy mục tiêu hoặc bạn không có quyền truy cập."));

        taskRepository.findTaskInGoalOwned(userId, goalId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        if (!taskRepository.deleteTask(taskId, goalId)) {
            throw new TaskNotFoundException();
        }

        taskRepository.incrementGoalVersion(userId, goalId);
    }

    @Transactional
    public void deleteTaskDirect(UUID userId, UUID taskId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");

        TaskJdbcRepository.StoredTask existing = taskRepository.findTaskOwned(userId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        deleteTask(userId, existing.goalId(), taskId);
    }

    @Transactional(readOnly = true)
    public DailyTasksResponse listTasksByDate(UUID userId, LocalDate date, int page, int limit) {
        Objects.requireNonNull(userId, "userId must not be null");

        LocalDate queryDate = date != null ? date : today();
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, Math.min(100, limit));
        int offset = (safePage - 1) * safeLimit;

        List<TaskResponse> tasks = taskRepository.listTasksByUserAndDate(userId, queryDate, offset, safeLimit)
                .stream().map(TaskJdbcRepository.StoredTask::toResponse).toList();
        long total = taskRepository.countTasksByUserAndDate(userId, queryDate);

        return DailyTasksResponse.of(
                queryDate.toString(),
                tasks,
                PaginationMeta.of(safePage, safeLimit, total)
        );
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listOverdueTasks(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return taskRepository.listOverdueTasks(userId, today())
                .stream().map(TaskJdbcRepository.StoredTask::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GoalProgressResponse getGoalProgress(UUID userId, UUID goalId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(goalId, "goalId must not be null");

        // Verify goal ownership
        goalRepository.findOwned(userId, goalId)
                .orElseThrow(() -> new TaskNotFoundException("Không tìm thấy mục tiêu hoặc bạn không có quyền truy cập."));

        TaskJdbcRepository.ProgressCount progress = taskRepository.getGoalProgress(userId, goalId);
        return GoalProgressResponse.of(goalId, progress.total(), progress.completed());
    }
}
