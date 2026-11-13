package com.studyplanner.sessions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.sessions.StudySessionPolicy.Status;

/**
 * Study sessions. The server decides every number: focus time comes from the injected Clock, never from the
 * client, and each write holds the owner lock, checks the Idempotency-Key receipt before the version, and
 * stores the receipt in the same transaction as the change.
 */
@Service
public class StudySessionService {
    public record SessionView(UUID id, UUID taskId, UUID goalId, String taskTitle, String subject, Status status,
                              int plannedFocusSeconds, int focusedSeconds, int remainingSeconds, boolean focusComplete,
                              Instant startedAt, Instant lastResumedAt, Instant endedAt, Instant serverNow,
                              int version) {}

    public record ActiveSessionView(SessionView session) {}

    public record WriteResult(SessionView session, boolean replayed) {}

    public record SessionPage(List<SessionView> items, long totalItems) {}

    private final StudySessionRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public StudySessionService(StudySessionRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ActiveSessionView active(UUID owner) {
        Instant now = clock.instant();
        return new ActiveSessionView(repository.findActive(owner).map(s -> view(s, now)).orElse(null));
    }

    @Transactional(readOnly = true)
    public SessionView get(UUID owner, UUID id) {
        Instant now = clock.instant();
        return view(repository.find(owner, id, false).orElseThrow(StudySessionException::notFound), now);
    }

    @Transactional(readOnly = true)
    public SessionPage list(UUID owner, UUID taskId, int page, int limit) {
        Instant now = clock.instant();
        long offset = (long) (page - 1) * limit;
        return new SessionPage(repository.list(owner, taskId, limit, offset).stream().map(s -> view(s, now)).toList(),
                repository.count(owner, taskId));
    }

    @Transactional(readOnly = true)
    public StudySessionRepository.StudyTime studyTime(UUID owner, UUID goalId) {
        if (!repository.goalOwned(owner, goalId)) throw StudySessionException.goalNotFound();
        return repository.studyTime(owner, goalId);
    }

    @Transactional
    public WriteResult start(UUID owner, UUID key, StudySessionRequest.Start request) {
        repository.lockOwner(owner);
        String fingerprint = fingerprint("START", request.taskId().toString());
        Optional<WriteResult> replay = replay(owner, key, fingerprint);
        if (replay.isPresent()) return replay.get();

        var task = repository.findOwnedTask(owner, request.taskId()).orElseThrow(StudySessionException::taskNotFound);
        Optional<StudySessionRepository.Stored> active = repository.findActive(owner);
        if (active.isPresent()) throw StudySessionException.alreadyActive(active.get().id());

        Instant now = clock.instant();
        StudySessionRepository.Stored created;
        try {
            created = repository.insertRunning(owner, task, now);
        } catch (DuplicateKeyException race) {
            // The partial unique index is the backstop; the owner lock normally makes this unreachable.
            throw StudySessionException.alreadyActive(null);
        }
        SessionView view = view(created, now);
        repository.saveReceipt(owner, key, fingerprint, json(view));
        return new WriteResult(view, false);
    }

    @Transactional
    public WriteResult action(UUID owner, UUID id, UUID key, StudySessionRequest.ActionRequest request) {
        repository.lockOwner(owner);
        String fingerprint = fingerprint("ACTION", id + ":" + request.action() + ":" + request.expectedVersion());
        // The key is replayed before the version check so a retry after a timeout is not reported as stale.
        Optional<WriteResult> replay = replay(owner, key, fingerprint);
        if (replay.isPresent()) return replay.get();

        var current = repository.find(owner, id, true).orElseThrow(StudySessionException::notFound);
        if (current.version() != request.expectedVersion()) {
            throw StudySessionException.versionConflict(current.version());
        }
        Instant now = clock.instant();
        var next = StudySessionPolicy.apply(request.action(), current.status(), current.accumulatedSeconds(),
                current.lastResumedAt(), now);
        SessionView view = view(repository.update(current, next), now);
        repository.saveReceipt(owner, key, fingerprint, json(view));
        return new WriteResult(view, false);
    }

    private Optional<WriteResult> replay(UUID owner, UUID key, String fingerprint) {
        return repository.receipt(owner, key).map(receipt -> {
            if (!receipt.fingerprint().equals(fingerprint)) throw StudySessionException.idempotencyConflict();
            try {
                return new WriteResult(mapper.readValue(receipt.responseJson(), SessionView.class), true);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Stored session receipt is unreadable", e);
            }
        });
    }

    static SessionView view(StudySessionRepository.Stored s, Instant now) {
        int focused = StudySessionPolicy.focusedSeconds(s.status(), s.accumulatedSeconds(), s.lastResumedAt(), now);
        return new SessionView(s.id(), s.taskId(), s.goalId(), s.taskTitle(), s.subject(), s.status(),
                StudySessionPolicy.PLANNED_FOCUS_SECONDS, focused, StudySessionPolicy.remainingSeconds(focused),
                s.status().active() && focused >= StudySessionPolicy.PLANNED_FOCUS_SECONDS,
                s.startedAt(), s.lastResumedAt(), s.endedAt(), now, s.version());
    }

    private String json(SessionView view) {
        try {
            return mapper.writeValueAsString(view);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot store session receipt", e);
        }
    }

    private static String fingerprint(String operation, String payload) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((operation + ":" + payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
