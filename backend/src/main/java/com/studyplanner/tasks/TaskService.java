package com.studyplanner.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyplanner.goals.GoalJdbcRepository;
import com.studyplanner.goals.GoalService;

/**
 * Task reads and the done/re-open transition. The owner ID comes from the authenticated principal.
 * Manual CRUD validates dates and budgets under the same goal lock.
 */
@Service
public class TaskService {
    private final TaskJdbcRepository tasks;
    private final GoalJdbcRepository goals;

    public TaskService(TaskJdbcRepository tasks, GoalJdbcRepository goals) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.goals = Objects.requireNonNull(goals, "goals");
    }

    /** One page of tasks plus the total number that match. */
    public record TaskPage(List<TaskJdbcRepository.StoredTask> items, long totalItems) {}

    @Transactional(readOnly = true)
    public TaskPage listByGoal(UUID owner, UUID goalId, LocalDate date, Boolean completed, int page, int limit) {
        requireOwner(owner);
        // An empty list must still be refused for a goal the caller does not own.
        goals.findOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        return new TaskPage(tasks.listByGoal(owner, goalId, date, completed, limit, offset(page, limit)),
                tasks.countByGoal(owner, goalId, date, completed));
    }

    @Transactional(readOnly = true)
    public TaskPage listByDate(UUID owner, LocalDate date, int page, int limit) {
        requireOwner(owner);
        return new TaskPage(tasks.listByDate(owner, date, limit, offset(page, limit)),
                tasks.countByDate(owner, date));
    }

    @Transactional(readOnly = true)
    public TaskJdbcRepository.StoredTask getTask(UUID owner, UUID goalId, UUID taskId) {
        requireOwner(owner);
        return tasks.findOwned(owner, goalId, taskId).orElseThrow(TaskNotFoundException::new);
    }

    /**
     * Sets the completed flag to the requested state. The goal row is locked first (the same lock goal edits
     * use). A real change bumps the goal version once; asking for the state the task already has changes nothing.
     */
    @Transactional
    public TaskJdbcRepository.StoredTask setCompleted(UUID owner, UUID goalId, UUID taskId, boolean completed) {
        requireOwner(owner);
        goals.lockOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        TaskJdbcRepository.StoredTask current = tasks.findOwned(owner, goalId, taskId)
                .orElseThrow(TaskNotFoundException::new);
        if (current.completed() == completed) {
            return current;
        }
        if (!tasks.updateCompleted(owner, goalId, taskId, completed)) {
            throw new TaskNotFoundException();
        }
        goals.bumpVersion(owner, goalId);
        return tasks.findOwned(owner, goalId, taskId).orElseThrow(TaskNotFoundException::new);
    }

    @Transactional
    public TaskJdbcRepository.StoredTask createTask(UUID owner, UUID goalId, TaskPolicy.TaskInput input) {
        var goal = goals.lockOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        validateForGoal(owner, goal, input, null);
        var stored = tasks.insertOwned(owner, goalId, input, "manual");
        goals.bumpVersion(owner, goalId);
        return stored;
    }

    @Transactional
    public TaskJdbcRepository.StoredTask updateTask(UUID owner, UUID goalId, UUID taskId, TaskPatch patch) {
        var goal = goals.lockOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        var current = tasks.findOwned(owner, goalId, taskId).orElseThrow(TaskNotFoundException::new);
        var input = new TaskPolicy.TaskInput(patch.title() == null ? current.title() : patch.title().trim(),
                patch.scheduledDate() == null ? current.scheduledDate() : patch.scheduledDate(),
                patch.estimatedMinutes() == null ? current.estimatedMinutes() : patch.estimatedMinutes());
        boolean completed = patch.completed() == null ? current.completed() : patch.completed();
        validateForGoal(owner, goal, input, taskId);
        if (input.title().equals(current.title()) && input.scheduledDate().equals(current.scheduledDate())
                && input.estimatedMinutes() == current.estimatedMinutes() && completed == current.completed()) {
            return current;
        }
        if (!tasks.updateOwned(owner, goalId, taskId, input, completed)) throw new TaskNotFoundException();
        goals.bumpVersion(owner, goalId);
        return tasks.findOwned(owner, goalId, taskId).orElseThrow(TaskNotFoundException::new);
    }

    @Transactional
    public TaskJdbcRepository.StoredTask updateTaskDirect(UUID owner, UUID taskId, TaskPatch patch) {
        var existing = tasks.findOwnedById(owner, taskId).orElseThrow(TaskNotFoundException::new);
        return updateTask(owner, existing.goalId(), taskId, patch);
    }

    @Transactional
    public void deleteTask(UUID owner, UUID goalId, UUID taskId) {
        goals.lockOwned(owner, goalId).orElseThrow(GoalService.GoalNotFoundException::new);
        if (!tasks.deleteOwned(owner, goalId, taskId)) throw new TaskNotFoundException();
        goals.bumpVersion(owner, goalId);
    }

    @Transactional(readOnly = true)
    public List<TaskJdbcRepository.StoredTask> listOverdue(UUID owner) {
        return tasks.listOverdue(Objects.requireNonNull(owner));
    }

    private void validateForGoal(UUID owner, GoalJdbcRepository.StoredGoal goal,
                                 TaskPolicy.TaskInput input, UUID excludeId) {
        var errors = new java.util.ArrayList<>(TaskPolicy.validateInput(input));
        errors.addAll(TaskPolicy.validateWithinGoal(input.scheduledDate(), goal.startDate(), goal.examDate()));
        if (!errors.isEmpty()) throw new TaskValidationException(errors);
        TaskPolicy.checkDailyBudget(input.scheduledDate(),
                tasks.dailyMinutes(owner, goal.id(), input.scheduledDate(), excludeId),
                input.estimatedMinutes(), goal.minutesPerDay());
    }

    public record TaskPatch(String title, LocalDate scheduledDate, Integer estimatedMinutes, Boolean completed) {}

    private static long offset(int page, int limit) {
        if (page < 1 || limit < 1) {
            throw new IllegalArgumentException("page and limit must be >= 1");
        }
        return (long) (page - 1) * limit;
    }

    private static void requireOwner(UUID owner) {
        Objects.requireNonNull(owner, "Authenticated server-side owner is required");
    }

    /** Unknown task, a task of another goal, or a task of another user: all the same 404. */
    public static final class TaskNotFoundException extends RuntimeException {
        public TaskNotFoundException() {
            super("Task not found");
        }
    }
}
