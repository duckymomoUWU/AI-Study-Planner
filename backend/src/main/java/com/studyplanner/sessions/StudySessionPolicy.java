package com.studyplanner.sessions;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure state and time rules of a focus session; no clock of its own (the caller passes "now").
 * Focus is the time spent RUNNING, floored to whole seconds, never more than the planned 1500 seconds.
 */
public final class StudySessionPolicy {
    public static final int PLANNED_FOCUS_SECONDS = 1500;

    public enum Status {
        RUNNING, PAUSED, COMPLETED, CANCELLED;

        public boolean active() {
            return this == RUNNING || this == PAUSED;
        }
    }

    public enum Action { PAUSE, RESUME, FINISH, CANCEL }

    /** The persistent part of a session after an action. */
    public record State(Status status, int accumulatedSeconds, Instant lastResumedAt, Instant endedAt) {}

    private StudySessionPolicy() {}

    /** Focused seconds at {@code now}: stored seconds plus the running stretch, capped at the plan. */
    public static int focusedSeconds(Status status, int accumulatedSeconds, Instant lastResumedAt, Instant now) {
        long total = accumulatedSeconds;
        if (status == Status.RUNNING && lastResumedAt != null) {
            total += Math.max(0, Duration.between(lastResumedAt, now).getSeconds());
        }
        return (int) Math.min(PLANNED_FOCUS_SECONDS, total);
    }

    public static int remainingSeconds(int focusedSeconds) {
        return Math.max(0, PLANNED_FOCUS_SECONDS - focusedSeconds);
    }

    /** Applies an action at {@code now}; throws a StudySessionException when the state does not allow it. */
    public static State apply(Action action, Status status, int accumulatedSeconds, Instant lastResumedAt, Instant now) {
        if (!status.active()) {
            throw StudySessionException.stateConflict(status.name(), verb(action));
        }
        int focused = focusedSeconds(status, accumulatedSeconds, lastResumedAt, now);
        return switch (action) {
            case PAUSE -> {
                if (status != Status.RUNNING) throw StudySessionException.stateConflict(status.name(), verb(action));
                if (focused >= PLANNED_FOCUS_SECONDS) throw StudySessionException.finishRequired();
                yield new State(Status.PAUSED, focused, null, null);
            }
            case RESUME -> {
                if (status != Status.PAUSED) throw StudySessionException.stateConflict(status.name(), verb(action));
                if (focused >= PLANNED_FOCUS_SECONDS) throw StudySessionException.finishRequired();
                yield new State(Status.RUNNING, focused, now, null);
            }
            case FINISH -> new State(Status.COMPLETED, focused, null, now);
            case CANCEL -> new State(Status.CANCELLED, focused, null, now);
        };
    }

    private static String verb(Action action) {
        return switch (action) {
            case PAUSE -> "tạm dừng";
            case RESUME -> "tiếp tục";
            case FINISH -> "kết thúc";
            case CANCEL -> "hủy";
        };
    }
}
