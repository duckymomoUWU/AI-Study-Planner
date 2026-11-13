package com.studyplanner.goals;

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

/**
 * Goal application boundary: composes policy and persistence inside transactions.
 * The owner ID must come from the authenticated principal (ASP-12), never from a request body, query or path.
 */
@Service
public class GoalService {
    private static final int MAX_TIMEZONE_LENGTH = 64;

    private final GoalJdbcRepository repository;
    private final Clock clock;
    private final ZoneId defaultZone;

    public GoalService(GoalJdbcRepository repository, Clock clock,
                       @Value("${app.default-timezone:Asia/Ho_Chi_Minh}") String timezone) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.defaultZone = ZoneId.of(Objects.requireNonNull(timezone, "timezone"));
    }

    /** One page of goals plus the total number of goals the owner has. */
    public record GoalPage(List<GoalJdbcRepository.StoredGoal> items, long totalItems) {}

    @Transactional(readOnly = true)
    public GoalPage listGoals(UUID trustedOwnerId, int page, int limit) {
        requireOwner(trustedOwnerId);
        if (page < 1 || limit < 1) {
            throw new IllegalArgumentException("page and limit must be >= 1");
        }
        long offset = (long) (page - 1) * limit;
        return new GoalPage(repository.listOwnedPage(trustedOwnerId, limit, offset),
                repository.countOwned(trustedOwnerId));
    }

    @Transactional(readOnly = true)
    public GoalJdbcRepository.StoredGoal getGoal(UUID trustedOwnerId, UUID goalId) {
        requireOwner(trustedOwnerId);
        return repository.findOwned(trustedOwnerId, requireGoalId(goalId))
                .orElseThrow(GoalNotFoundException::new);
    }

    /** {@code requestedTimezone} may be null: the server default is used. */
    @Transactional
    public GoalJdbcRepository.StoredGoal createGoal(UUID trustedOwnerId, GoalPolicy.GoalInput rawInput,
                                                     String requestedTimezone) {
        requireOwner(trustedOwnerId);
        GoalPolicy.GoalInput input = normalize(rawInput);
        List<GoalPolicy.Violation> errors = new ArrayList<>();
        ZoneId zone = resolveZone(requestedTimezone, defaultZone, errors);
        errors.addAll(GoalPolicy.validate(input, today(zone == null ? defaultZone : zone)));
        if (input.startDate() != null && input.startDate().isBefore(today(zone == null ? defaultZone : zone))) {
            errors.add(new GoalPolicy.Violation("startDate", "must_not_be_in_past",
                    "Ngày bắt đầu không được trước hôm nay."));
        }
        validate(errors);
        if(input.planningScope()!=null) input=withScope(input,GoalPlanning.normalize(input.planningScope(),input));
        return repository.insertOwned(trustedOwnerId, input, zone.getId());
    }

    /**
     * Applies a partial change: lock the owned goal row, compare the version, merge the patch into the stored
     * goal, validate the goal as it would be afterwards together with its existing tasks, then write.
     * Any failure leaves the stored goal untouched.
     */
    @Transactional
    public GoalJdbcRepository.StoredGoal patchGoal(UUID trustedOwnerId, UUID goalId, GoalPatch patch,
                                                    int expectedVersion) {
        requireOwner(trustedOwnerId);
        requireGoalId(goalId);
        Objects.requireNonNull(patch, "patch");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be >= 0");
        }

        // Lock the parent row first. Task-writing services must take the same lock to avoid races.
        GoalJdbcRepository.StoredGoal current = repository.lockOwned(trustedOwnerId, goalId)
                .orElseThrow(GoalNotFoundException::new);
        if (current.version() != expectedVersion) {
            throw new GoalConflictException("stale_goal_version");
        }

        GoalPolicy.GoalInput merged = normalize(new GoalPolicy.GoalInput(
                patch.subject() != null ? patch.subject() : current.subject(),
                patch.topics() != null ? patch.topics() : current.topics(),
                patch.startDate() != null ? patch.startDate() : current.startDate(),
                patch.examDate() != null ? patch.examDate() : current.examDate(),
                patch.minutesPerDay() != null ? patch.minutesPerDay() : current.minutesPerDay()));

        List<GoalPolicy.Violation> errors = new ArrayList<>();
        ZoneId zone = resolveZone(patch.timezone(), ZoneId.of(current.timezone()), errors);
        if (zone != null) {
            List<GoalPolicy.ScheduledTask> existingTasks =
                    repository.listScheduledTasksOwned(trustedOwnerId, goalId);
            errors.addAll(GoalPolicy.validateUpdate(merged, existingTasks, today(zone)));
        }
        validate(errors);

        var planningScope=GoalPlanning.forPatch(current,merged,patch.planningScope());
        GoalPlanning.validateExisting(merged,planningScope,repository.listScheduledTasksOwned(trustedOwnerId,goalId));
        if (!repository.updateOwnedIfVersion(trustedOwnerId, goalId, merged, zone.getId(), expectedVersion)) {
            throw new GoalConflictException("stale_goal_version");
        }
        if(planningScope!=null) repository.updatePlanningScopeOwned(trustedOwnerId,goalId,planningScope,false);
        return repository.findOwned(trustedOwnerId, goalId).orElseThrow(GoalNotFoundException::new);
    }

    /** Save user-approved planning settings before calling AI; provider failure cannot erase them. */
    @Transactional
    public GoalJdbcRepository.StoredGoal savePlanningScope(UUID owner,UUID goalId,com.fasterxml.jackson.databind.JsonNode raw) {
        var current=repository.lockOwned(owner,goalId).orElseThrow(GoalNotFoundException::new);
        var input=GoalPlanning.input(current);var normalized=GoalPlanning.normalize(raw,input);
        GoalPlanning.validateExisting(input,normalized,repository.listScheduledTasksOwned(owner,goalId));
        if(normalized.equals(current.planningScope())) return current;
        repository.updatePlanningScopeOwned(owner,goalId,normalized,true);
        return repository.findOwned(owner,goalId).orElseThrow(GoalNotFoundException::new);
    }
    private static GoalPolicy.GoalInput withScope(GoalPolicy.GoalInput input,com.fasterxml.jackson.databind.JsonNode scope) {
        return new GoalPolicy.GoalInput(input.subject(),input.topics(),input.startDate(),input.examDate(),input.minutesPerDay(),scope);
    }

    @Transactional
    public void deleteGoal(UUID trustedOwnerId, UUID goalId) {
        requireOwner(trustedOwnerId);
        if (!repository.deleteOwned(trustedOwnerId, requireGoalId(goalId))) {
            throw new GoalNotFoundException();
        }
    }

    private LocalDate today(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    /** Returns the zone to use, or null (with an error recorded) when the requested name is not accepted. */
    private static ZoneId resolveZone(String requested, ZoneId fallback, List<GoalPolicy.Violation> errors) {
        if (requested == null) {
            return fallback;
        }
        if (requested.length() > MAX_TIMEZONE_LENGTH || !ZoneId.getAvailableZoneIds().contains(requested)) {
            errors.add(new GoalPolicy.Violation("timezone", "invalid_timezone",
                    "Múi giờ phải là tên IANA hợp lệ, ví dụ Asia/Ho_Chi_Minh."));
            return null;
        }
        return ZoneId.of(requested);
    }

    private static GoalPolicy.GoalInput normalize(GoalPolicy.GoalInput input) {
        Objects.requireNonNull(input, "goal input");
        return new GoalPolicy.GoalInput(
                input.subject() == null ? null : input.subject().trim(),
                input.topics() == null ? null : input.topics().trim(),
                input.startDate(),
                input.examDate(),
                input.minutesPerDay(),input.planningScope()
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
        private final transient List<GoalPolicy.Violation> violations;

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

    /** The stored version differs from the client's expectedVersion. */
    public static final class GoalConflictException extends RuntimeException {
        public GoalConflictException(String code) {
            super(code);
        }
    }
}
