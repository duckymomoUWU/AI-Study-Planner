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
 * Creating, editing and deleting tasks (budget and deadline checks) belong to ASP-10.
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
