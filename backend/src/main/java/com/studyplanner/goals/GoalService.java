package com.studyplanner.goals;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ASP-23/24 application boundary. It composes policy and persistence inside transactions.
 * The owner ID must come from a trusted authenticated server-side principal (ASP-12),
 * never from an HTTP request body or query parameter.
 */
@Service
public class GoalService {
    private final GoalJdbcRepository repository;
    private final Clock clock;
    private final ZoneId zoneId;

    public GoalService(GoalJdbcRepository repository, Clock clock,
                       @Value("${app.default-timezone:Asia/Ho_Chi_Minh}") String timezone) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zoneId = ZoneId.of(Objects.requireNonNull(timezone, "timezone"));
    }

    @Transactional(readOnly = true)
    public List<GoalJdbcRepository.StoredGoal> listGoals(UUID trustedOwnerId) {
        requireOwner(trustedOwnerId);
        return repository.listOwned(trustedOwnerId);
    }

    @Transactional(readOnly = true)
    public GoalJdbcRepository.StoredGoal getGoal(UUID trustedOwnerId, UUID goalId) {
        requireOwner(trustedOwnerId);
        return repository.findOwned(trustedOwnerId, requireGoalId(goalId))
                .orElseThrow(() -> new GoalNotFoundException());
    }

    @Transactional
    public GoalJdbcRepository.StoredGoal createGoal(UUID trustedOwnerId, GoalPolicy.GoalInput rawInput) {
        requireOwner(trustedOwnerId);
        GoalPolicy.GoalInput input = normalize(rawInput);
        validate(GoalPolicy.validate(input, today()));
        return repository.insertOwned(trustedOwnerId, input, zoneId.getId());
    }

    @Transactional
    public GoalJdbcRepository.StoredGoal updateGoal(UUID trustedOwnerId, UUID goalId,
                                                     GoalPolicy.GoalInput rawInput, int expectedVersion) {
        requireOwner(trustedOwnerId);
        requireGoalId(goalId);
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be >= 0");
        }

        // Lock the parent row first. Task-writing services must take the same lock to avoid races.
        GoalJdbcRepository.StoredGoal current = repository.lockOwned(trustedOwnerId, goalId)
                .orElseThrow(() -> new GoalNotFoundException());
        if (current.version() != expectedVersion) {
            throw new GoalConflictException("stale_goal_version");
        }

        GoalPolicy.GoalInput input = normalize(rawInput);
        List<GoalPolicy.ScheduledTask> existingTasks =
                repository.listScheduledTasksOwned(trustedOwnerId, goalId);
        validate(GoalPolicy.validateUpdate(input, existingTasks, today()));

        if (!repository.updateOwnedIfVersion(trustedOwnerId, goalId, input, expectedVersion)) {
            throw new GoalConflictException("stale_goal_version");
        }
        return repository.findOwned(trustedOwnerId, goalId)
                .orElseThrow(() -> new GoalNotFoundException());
    }

    @Transactional
    public void deleteGoal(UUID trustedOwnerId, UUID goalId) {
        requireOwner(trustedOwnerId);
        if (!repository.deleteOwned(trustedOwnerId, requireGoalId(goalId))) {
            throw new GoalNotFoundException();
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zoneId));
    }

    private static GoalPolicy.GoalInput normalize(GoalPolicy.GoalInput input) {
        Objects.requireNonNull(input, "goal input");
        return new GoalPolicy.GoalInput(
                input.subject() == null ? null : input.subject().trim(),
                input.topics() == null ? null : input.topics().trim(),
                input.startDate(),
                input.examDate(),
                input.minutesPerDay()
        );
    }

    private static void validate(List<GoalPolicy.Violation> violations) {
        if (!violations.isEmpty()) {
            throw new GoalValidationException(violations);
        }
    }

    private static UUID requireGoalId(UUID goalId) {
        return Objects.requireNonNull(goalId, "goalId");
    }

    private static void requireOwner(UUID trustedOwnerId) {
        Objects.requireNonNull(trustedOwnerId,
                "Authenticated server-side owner is required; client-supplied user IDs are not trusted");
    }

    public static final class GoalValidationException extends RuntimeException {
        private final List<GoalPolicy.Violation> violations;

        public GoalValidationException(List<GoalPolicy.Violation> violations) {
            super("Goal validation failed");
            this.violations = List.copyOf(violations);
        }

        public List<GoalPolicy.Violation> violations() {
            return violations;
        }
    }

    public static final class GoalNotFoundException extends RuntimeException {
        public GoalNotFoundException() {
            super("Goal not found");
        }
    }

    public static final class GoalConflictException extends RuntimeException {
        public GoalConflictException(String code) {
            super(code);
        }
    }
}
