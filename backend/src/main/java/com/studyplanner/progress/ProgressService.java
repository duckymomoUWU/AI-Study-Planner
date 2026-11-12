package com.studyplanner.progress;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyplanner.goals.GoalService;

/**
 * Progress = completed tasks / total tasks of a goal (requirements.md). It says nothing about how well the
 * student understands the material. With no tasks it is 0 of 0 and the rate is 0.
 */
@Service
public class ProgressService {
    private final ProgressJdbcRepository repository;

    public ProgressService(ProgressJdbcRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public record Progress(UUID goalId, long totalTasks, long completedTasks, long remainingTasks,
                           double completionRate) {}

    @Transactional(readOnly = true)
    public Progress getProgress(UUID trustedOwnerId, UUID goalId) {
        Objects.requireNonNull(trustedOwnerId, "Authenticated server-side owner is required");
        ProgressJdbcRepository.TaskCounts counts = repository.countOwned(trustedOwnerId, goalId)
                .orElseThrow(GoalService.GoalNotFoundException::new);
        double rate = counts.total() == 0 ? 0.0 : (double) counts.completed() / counts.total();
        return new Progress(goalId, counts.total(), counts.completed(),
                counts.total() - counts.completed(), rate);
    }
}
