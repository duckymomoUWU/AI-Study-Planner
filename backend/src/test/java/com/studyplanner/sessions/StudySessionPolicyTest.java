package com.studyplanner.sessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.studyplanner.sessions.StudySessionPolicy.Action;
import com.studyplanner.sessions.StudySessionPolicy.State;
import com.studyplanner.sessions.StudySessionPolicy.Status;

/** Clock and state rules of a focus session with a fixed "now"; no sleeping. */
class StudySessionPolicyTest {
    private static final Instant T0 = Instant.parse("2026-11-11T02:00:00Z");

    private static Instant after(long seconds) {
        return T0.plusSeconds(seconds);
    }

    @Test
    void runningFocusIsStoredSecondsPlusTheRunningStretchFlooredToSeconds() {
        assertEquals(120, StudySessionPolicy.focusedSeconds(Status.RUNNING, 0, T0, after(120)));
        assertEquals(150, StudySessionPolicy.focusedSeconds(Status.RUNNING, 30, T0, T0.plusMillis(120_999)));
        assertEquals(0, StudySessionPolicy.focusedSeconds(Status.RUNNING, 0, T0, T0.plusMillis(999)));
    }

    @Test
    void pausedAndFinishedSessionsDoNotGrowWithWallClockTime() {
        assertEquals(300, StudySessionPolicy.focusedSeconds(Status.PAUSED, 300, null, after(86_400)));
        assertEquals(300, StudySessionPolicy.focusedSeconds(Status.COMPLETED, 300, null, after(86_400)));
        assertEquals(300, StudySessionPolicy.focusedSeconds(Status.CANCELLED, 300, null, after(86_400)));
    }

    @Test
    void focusIsCappedAtTheFullPomodoroEvenAfterAnOvernightGap() {
        assertEquals(1500, StudySessionPolicy.focusedSeconds(Status.RUNNING, 0, T0, after(8 * 3600)));
        assertEquals(1500, StudySessionPolicy.focusedSeconds(Status.RUNNING, 1400, T0, after(3600)));
        assertEquals(0, StudySessionPolicy.remainingSeconds(1500));
        assertEquals(1380, StudySessionPolicy.remainingSeconds(120));
    }

    @Test
    void aClockThatMovesBackwardsNeverSubtractsFocus() {
        assertEquals(30, StudySessionPolicy.focusedSeconds(Status.RUNNING, 30, T0, after(-60)));
    }

    @Test
    void pauseStoresTheRunningTimeAndClearsTheResumePoint() {
        State next = StudySessionPolicy.apply(Action.PAUSE, Status.RUNNING, 10, T0, after(120));
        assertEquals(Status.PAUSED, next.status());
        assertEquals(130, next.accumulatedSeconds());
        assertNull(next.lastResumedAt());
        assertNull(next.endedAt());
    }

    @Test
    void resumeStartsANewStretchAndKeepsTheStoredSeconds() {
        State next = StudySessionPolicy.apply(Action.RESUME, Status.PAUSED, 130, null, after(900));
        assertEquals(Status.RUNNING, next.status());
        assertEquals(130, next.accumulatedSeconds());
        assertEquals(after(900), next.lastResumedAt());
    }

    @Test
    void finishSavesTheServerComputedSecondsEvenBeforeTwentyFiveMinutes() {
        State fromRunning = StudySessionPolicy.apply(Action.FINISH, Status.RUNNING, 0, T0, after(61));
        assertEquals(Status.COMPLETED, fromRunning.status());
        assertEquals(61, fromRunning.accumulatedSeconds());
        assertEquals(after(61), fromRunning.endedAt());
        State fromPaused = StudySessionPolicy.apply(Action.FINISH, Status.PAUSED, 200, null, after(5000));
        assertEquals(200, fromPaused.accumulatedSeconds());
    }

    @Test
    void finishAfterTheFocusExpiredSavesExactlyTheFullPomodoro() {
        State next = StudySessionPolicy.apply(Action.FINISH, Status.RUNNING, 0, T0, after(10 * 3600));
        assertEquals(1500, next.accumulatedSeconds());
    }

    @Test
    void cancelKeepsTheSecondsForHistoryAndEndsTheSession() {
        State next = StudySessionPolicy.apply(Action.CANCEL, Status.RUNNING, 0, T0, after(90));
        assertEquals(Status.CANCELLED, next.status());
        assertEquals(90, next.accumulatedSeconds());
        assertEquals(after(90), next.endedAt());
    }

    @Test
    void pauseAndResumeAreRejectedOnceTheFocusIsComplete() {
        var pause = assertThrows(StudySessionException.class,
                () -> StudySessionPolicy.apply(Action.PAUSE, Status.RUNNING, 0, T0, after(1500)));
        assertEquals("FOCUS_FINISH_REQUIRED", pause.code());
        var resume = assertThrows(StudySessionException.class,
                () -> StudySessionPolicy.apply(Action.RESUME, Status.PAUSED, 1500, null, after(1)));
        assertEquals("FOCUS_FINISH_REQUIRED", resume.code());
    }

    @Test
    void actionsInTheWrongStateAreStateConflicts() {
        assertConflict(Action.PAUSE, Status.PAUSED);
        assertConflict(Action.RESUME, Status.RUNNING);
        for (Action action : Action.values()) {
            assertConflict(action, Status.COMPLETED);
            assertConflict(action, Status.CANCELLED);
        }
    }

    private static void assertConflict(Action action, Status status) {
        var error = assertThrows(StudySessionException.class,
                () -> StudySessionPolicy.apply(action, status, 100, null, after(10)));
        assertEquals(409, error.status());
        assertEquals("SESSION_STATE_CONFLICT", error.code());
    }
}
