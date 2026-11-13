package com.studyplanner.sessions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Study sessions over real HTTP, session auth and PostgreSQL, with a controllable clock instead of sleeping.
 * Only for a disposable database: mvn -f backend/pom.xml test -Dtest=StudySessionApiPostgresIntegrationTest
 * -Dgoal.integration=true (DB_URL, DB_USER and DB_PASSWORD point at study_planner_test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(StudySessionApiPostgresIntegrationTest.ClockConfig.class)
@EnabledIfSystemProperty(named = "goal.integration", matches = "true")
class StudySessionApiPostgresIntegrationTest {
    private static final Instant T0 = Instant.parse("2026-11-11T02:00:00Z");

    /** A clock the test moves by hand. */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(T0);

        void set(Instant instant) { now.set(instant); }
        void advance(long seconds) { now.updateAndGet(i -> i.plusSeconds(seconds)); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void clean() throws Exception {
        try (var c = jdbc.getDataSource().getConnection()) {
            assertTrue(c.getMetaData().getURL().endsWith("study_planner_test"), "Disposable test DB required");
        }
        jdbc.update("DELETE FROM app_users");
        clock.set(T0);
    }

    private MockHttpSession login(String email) throws Exception {
        String creds = "{\"email\":\"" + email + "\",\"password\":\"Password123!\"}";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(creds))
                .andExpect(status().isCreated());
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(creds)).andExpect(status().isOk()).andReturn();
        jdbc.update("UPDATE app_users SET study_daily_minutes=1440 WHERE email=?", email);
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String goal(MockHttpSession s, String subject) throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String body = "{\"subject\":\"" + subject + "\",\"topics\":\"Chủ đề\",\"startDate\":\"" + today
                + "\",\"examDate\":\"" + today.plusDays(30) + "\",\"minutesPerDay\":120}";
        String res = mvc.perform(post("/api/v1/goals").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("data").get("id").asText();
    }

    private String task(String goalId, String title) {
        return jdbc.queryForObject("INSERT INTO study_tasks (goal_id, title, scheduled_date, estimated_minutes) "
                + "VALUES (?::uuid, ?, current_date, 45) RETURNING id::text", String.class, goalId, title);
    }

    private ResultActions start(MockHttpSession s, String taskId, String key) throws Exception {
        return mvc.perform(post("/api/v1/study-sessions").session(s).with(csrf()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":\"" + taskId + "\"}"));
    }

    private ResultActions act(MockHttpSession s, String sessionId, String action, int version, String key) throws Exception {
        return mvc.perform(post("/api/v1/study-sessions/" + sessionId + "/actions").session(s).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\",\"expectedVersion\":" + version + "}"));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private int sessionRows() {
        return jdbc.queryForObject("SELECT count(*) FROM study_sessions", Integer.class);
    }

    @Test
    void startPauseResumeFinishCountsOnlyRunningTimeAndStoresIt() throws Exception {
        var s = login("a@example.com");
        String goalId = goal(s, "Kiểm thử");
        String taskId = task(goalId, "Ôn hộp đen");

        JsonNode started = data(start(s, taskId, key()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.taskTitle").value("Ôn hộp đen"))
                .andExpect(jsonPath("$.data.subject").value("Kiểm thử"))
                .andExpect(jsonPath("$.data.plannedFocusSeconds").value(1500))
                .andExpect(jsonPath("$.data.focusedSeconds").value(0)));
        String id = started.get("id").asText();

        clock.advance(120);
        mvc.perform(get("/api/v1/study-sessions/active").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.id").value(id))
                .andExpect(jsonPath("$.data.session.focusedSeconds").value(120))
                .andExpect(jsonPath("$.data.session.remainingSeconds").value(1380));

        JsonNode paused = data(act(s, id, "PAUSE", 0, key()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.focusedSeconds").value(120))
                .andExpect(jsonPath("$.data.lastResumedAt").value((Object) null))
                .andExpect(jsonPath("$.data.version").value(1)));
        assertEquals(1, paused.get("version").asInt());

        clock.advance(3600); // a long break while paused must not count
        mvc.perform(get("/api/v1/study-sessions/" + id).session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.focusedSeconds").value(120));

        act(s, id, "RESUME", 1, key()).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));
        clock.advance(60);
        act(s, id, "FINISH", 2, key()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.focusedSeconds").value(180))
                .andExpect(jsonPath("$.data.endedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.version").value(3));

        mvc.perform(get("/api/v1/study-sessions/active").session(s)).andExpect(jsonPath("$.data.session").value((Object) null));
        mvc.perform(get("/api/v1/goals/" + goalId + "/study-time").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedSessionCount").value(1))
                .andExpect(jsonPath("$.data.totalFocusSeconds").value(180));

        // Studying never changes the plan: the task keeps its minutes and is not marked done.
        assertEquals(45, jdbc.queryForObject("SELECT estimated_minutes FROM study_tasks WHERE id=?::uuid", Integer.class, taskId));
        assertFalse(jdbc.queryForObject("SELECT completed FROM study_tasks WHERE id=?::uuid", Boolean.class, taskId));
        assertEquals(0, jdbc.queryForObject("SELECT version FROM study_goals WHERE id=?::uuid", Integer.class, goalId));
    }

    @Test
    void onlyOneActiveSessionAndTheConflictNamesTheExistingOne() throws Exception {
        var s = login("a@example.com");
        String goalId = goal(s, "Toán");
        String t1 = task(goalId, "Một");
        String t2 = task(goalId, "Hai");
        String id = data(start(s, t1, key()).andExpect(status().isCreated())).get("id").asText();

        start(s, t2, key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_ALREADY_ACTIVE"))
                .andExpect(jsonPath("$.error.context.activeSessionId").value(id));
        assertEquals(1, sessionRows());

        act(s, id, "PAUSE", 0, key()).andExpect(status().isOk());
        start(s, t2, key()).andExpect(status().isConflict()); // PAUSED is still active
        act(s, id, "CANCEL", 1, key()).andExpect(status().isOk());
        start(s, t2, key()).andExpect(status().isCreated()); // a finished session frees the slot
        assertEquals(2, sessionRows());
    }

    @Test
    void simultaneousStartsWithDifferentKeysCreateExactlyOneSession() throws Exception {
        var s = login("a@example.com");
        String taskId = task(goal(s, "Toán"), "Song song");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(() -> start(s, taskId, key()).andReturn().getResponse().getStatus());
            }
            int created = 0, conflicts = 0;
            for (Future<Integer> f : pool.invokeAll(calls)) {
                int code = f.get();
                if (code == 201) created++;
                else if (code == 409) conflicts++;
            }
            assertEquals(1, created);
            assertEquals(5, conflicts);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, sessionRows());
    }

    @Test
    void simultaneousActionsOnTheSameVersionApplyExactlyOneAndRejectTheRest() throws Exception {
        var s = login("a@example.com");
        String id = data(start(s, task(goal(s, "Toán"), "Hai tab"), key()).andExpect(status().isCreated())).get("id").asText();
        clock.advance(100);

        // Two tabs holding version 0 press different buttons with different keys, several times over.
        List<String> actions = List.of("PAUSE", "CANCEL", "FINISH", "PAUSE", "CANCEL", "FINISH");
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        int applied = 0, stale = 0;
        try {
            List<Callable<ResultSnapshot>> calls = new java.util.ArrayList<>();
            for (String action : actions) {
                calls.add(() -> {
                    var response = act(s, id, action, 0, key()).andReturn().getResponse();
                    return new ResultSnapshot(response.getStatus(), response.getContentAsString());
                });
            }
            for (Future<ResultSnapshot> f : pool.invokeAll(calls)) {
                ResultSnapshot result = f.get();
                if (result.status() == 200) {
                    applied++;
                } else {
                    assertEquals(409, result.status());
                    assertEquals("SESSION_VERSION_CONFLICT", mapper.readTree(result.body()).at("/error/code").asText());
                    assertEquals(1, mapper.readTree(result.body()).at("/error/context/currentVersion").asInt());
                    stale++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, applied);
        assertEquals(actions.size() - 1, stale);

        // Exactly one transition happened: version 1, the 100 seconds counted once, one receipt per accepted write.
        assertEquals(1, jdbc.queryForObject("SELECT version FROM study_sessions WHERE id=?::uuid", Integer.class, id));
        assertEquals(100, jdbc.queryForObject("SELECT accumulated_focus_seconds FROM study_sessions WHERE id=?::uuid", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM study_session_receipts", Integer.class));
    }

    @Test
    void simultaneousRetriesWithTheSameKeyRecordTheChangeOnceAndAgreeOnTheResult() throws Exception {
        var s = login("a@example.com");
        String id = data(start(s, task(goal(s, "Toán"), "Retry"), key()).andExpect(status().isCreated())).get("id").asText();
        clock.advance(240);
        String finishKey = key();

        ExecutorService pool = Executors.newFixedThreadPool(6);
        java.util.Set<Integer> seconds = new java.util.HashSet<>();
        try {
            List<Callable<ResultSnapshot>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(() -> {
                    var response = act(s, id, "FINISH", 0, finishKey).andReturn().getResponse();
                    return new ResultSnapshot(response.getStatus(), response.getContentAsString());
                });
            }
            for (Future<ResultSnapshot> f : pool.invokeAll(calls)) {
                ResultSnapshot result = f.get();
                assertEquals(200, result.status());
                JsonNode session = mapper.readTree(result.body()).get("data");
                assertEquals("COMPLETED", session.get("status").asText());
                seconds.add(session.get("focusedSeconds").asInt());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(java.util.Set.of(240), seconds);
        assertEquals(1, jdbc.queryForObject("SELECT version FROM study_sessions WHERE id=?::uuid", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM study_session_receipts", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM study_sessions WHERE status='COMPLETED'", Integer.class));
    }

    private record ResultSnapshot(int status, String body) {}

    @Test
    void retryingWithTheSameKeyReplaysTheResultWithoutAnotherSessionOrDoubleTime() throws Exception {
        var s = login("a@example.com");
        String taskId = task(goal(s, "Toán"), "Replay");
        String startKey = key();
        JsonNode first = data(start(s, taskId, startKey).andExpect(status().isCreated()));
        clock.advance(500);
        JsonNode again = data(start(s, taskId, startKey).andExpect(status().isOk()));
        assertEquals(first.get("id").asText(), again.get("id").asText());
        assertEquals(0, again.get("focusedSeconds").asInt()); // the stored first response, not a recomputation
        assertEquals(1, sessionRows());

        String id = first.get("id").asText();
        String finishKey = key();
        clock.advance(100);
        JsonNode finished = data(act(s, id, "FINISH", 0, finishKey).andExpect(status().isOk()));
        clock.advance(900);
        JsonNode replay = data(act(s, id, "FINISH", 0, finishKey).andExpect(status().isOk()));
        assertEquals(finished.get("focusedSeconds").asInt(), replay.get("focusedSeconds").asInt());
        assertEquals(600, replay.get("focusedSeconds").asInt());
        assertEquals(1, jdbc.queryForObject("SELECT version FROM study_sessions WHERE id=?::uuid", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM study_session_receipts", Integer.class));
    }

    @Test
    void aKeyReusedForADifferentRequestIsAnIdempotencyConflict() throws Exception {
        var s = login("a@example.com");
        String goalId = goal(s, "Toán");
        String t1 = task(goalId, "A");
        String t2 = task(goalId, "B");
        String reused = key();
        String id = data(start(s, t1, reused).andExpect(status().isCreated())).get("id").asText();

        start(s, t2, reused).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        act(s, id, "PAUSE", 0, reused).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM study_sessions WHERE id=?::uuid", String.class, id));
    }

    @Test
    void aStaleVersionIsRejectedAndChangesNothing() throws Exception {
        var s = login("a@example.com");
        String id = data(start(s, task(goal(s, "Toán"), "Tab"), key()).andExpect(status().isCreated())).get("id").asText();
        clock.advance(30);
        act(s, id, "PAUSE", 0, key()).andExpect(status().isOk());

        clock.advance(500);
        act(s, id, "FINISH", 0, key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].field").value("expectedVersion"))
                .andExpect(jsonPath("$.error.context.currentVersion").value(1));
        assertEquals("PAUSED", jdbc.queryForObject("SELECT status FROM study_sessions WHERE id=?::uuid", String.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT version FROM study_sessions WHERE id=?::uuid", Integer.class, id));
        assertEquals(30, jdbc.queryForObject("SELECT accumulated_focus_seconds FROM study_sessions WHERE id=?::uuid", Integer.class, id));
    }

    @Test
    void invalidTransitionsAreStateConflictsAndTerminalSessionsNeverReopen() throws Exception {
        var s = login("a@example.com");
        String id = data(start(s, task(goal(s, "Toán"), "State"), key()).andExpect(status().isCreated())).get("id").asText();

        act(s, id, "RESUME", 0, key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_STATE_CONFLICT"));
        clock.advance(10);
        act(s, id, "CANCEL", 0, key()).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        for (String action : List.of("PAUSE", "RESUME", "FINISH", "CANCEL")) {
            act(s, id, action, 1, key()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("SESSION_STATE_CONFLICT"));
        }
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM study_sessions WHERE id=?::uuid", String.class, id));
    }

    @Test
    void closingTheTabPastTwentyFiveMinutesNeverCountsMoreThanTheFullPomodoro() throws Exception {
        var s = login("a@example.com");
        String goalId = goal(s, "Toán");
        String id = data(start(s, task(goalId, "Qua đêm"), key()).andExpect(status().isCreated())).get("id").asText();

        clock.advance(8 * 3600);
        mvc.perform(get("/api/v1/study-sessions/" + id).session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING")) // a GET never writes FINISH
                .andExpect(jsonPath("$.data.focusedSeconds").value(1500))
                .andExpect(jsonPath("$.data.remainingSeconds").value(0))
                .andExpect(jsonPath("$.data.focusComplete").value(true));
        assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM study_sessions WHERE id=?::uuid", String.class, id));

        act(s, id, "PAUSE", 0, key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FOCUS_FINISH_REQUIRED"));
        act(s, id, "FINISH", 0, key()).andExpect(status().isOk()).andExpect(jsonPath("$.data.focusedSeconds").value(1500));
        mvc.perform(get("/api/v1/goals/" + goalId + "/study-time").session(s))
                .andExpect(jsonPath("$.data.totalFocusSeconds").value(1500));
    }

    @Test
    void cancelledAndOtherGoalSessionsAreNotCountedInStudyTime() throws Exception {
        var s = login("a@example.com");
        String g1 = goal(s, "Toán");
        String g2 = goal(s, "Văn");
        String t1 = task(g1, "Một");
        String t2 = task(g2, "Hai");

        String cancelled = data(start(s, t1, key()).andExpect(status().isCreated())).get("id").asText();
        clock.advance(200);
        act(s, cancelled, "CANCEL", 0, key()).andExpect(status().isOk());
        String done = data(start(s, t1, key()).andExpect(status().isCreated())).get("id").asText();
        clock.advance(300);
        act(s, done, "FINISH", 0, key()).andExpect(status().isOk());
        String other = data(start(s, t2, key()).andExpect(status().isCreated())).get("id").asText();
        clock.advance(400);
        act(s, other, "FINISH", 0, key()).andExpect(status().isOk());

        mvc.perform(get("/api/v1/goals/" + g1 + "/study-time").session(s))
                .andExpect(jsonPath("$.data.completedSessionCount").value(1))
                .andExpect(jsonPath("$.data.totalFocusSeconds").value(300));
        mvc.perform(get("/api/v1/goals/" + g2 + "/study-time").session(s))
                .andExpect(jsonPath("$.data.totalFocusSeconds").value(400));
        mvc.perform(get("/api/v1/study-sessions").session(s)).andExpect(jsonPath("$.meta.totalItems").value(3));
    }

    @Test
    void anotherAccountSeesNothingAndCannotChangeOrGuessIds() throws Exception {
        var a = login("a@example.com");
        var b = login("b@example.com");
        String goalA = goal(a, "Toán");
        String taskA = task(goalA, "Riêng tư");
        String id = data(start(a, taskA, key()).andExpect(status().isCreated())).get("id").asText();

        start(b, taskA, key()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/study-sessions/" + id).session(b)).andExpect(status().isNotFound());
        act(b, id, "FINISH", 0, key()).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/goals/" + goalA + "/study-time").session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/study-sessions/active").session(b)).andExpect(jsonPath("$.data.session").value((Object) null));
        mvc.perform(get("/api/v1/study-sessions").session(b)).andExpect(jsonPath("$.meta.totalItems").value(0));
        mvc.perform(get("/api/v1/study-sessions?taskId=" + taskA).session(b)).andExpect(jsonPath("$.meta.totalItems").value(0));
        start(b, UUID.randomUUID().toString(), key()).andExpect(status().isNotFound());
        assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM study_sessions WHERE id=?::uuid", String.class, id));
        // The same key used by two owners is two different receipts.
        String shared = key();
        String taskB = task(goal(b, "Văn"), "Của B");
        start(b, taskB, shared).andExpect(status().isCreated());
        act(a, id, "PAUSE", 0, shared).andExpect(status().isOk());
    }

    @Test
    void deletingTheTaskOrGoalKeepsTheHistoryWithItsSnapshot() throws Exception {
        var s = login("a@example.com");
        String goalId = goal(s, "Lịch sử");
        String taskId = task(goalId, "Sẽ bị xóa");
        String id = data(start(s, taskId, key()).andExpect(status().isCreated())).get("id").asText();

        jdbc.update("DELETE FROM study_tasks WHERE id=?::uuid", taskId);
        mvc.perform(get("/api/v1/study-sessions/" + id).session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value((Object) null))
                .andExpect(jsonPath("$.data.taskTitle").value("Sẽ bị xóa"));
        clock.advance(90);
        act(s, id, "FINISH", 0, key()).andExpect(status().isOk()); // an open session can still finish

        jdbc.update("DELETE FROM study_goals WHERE id=?::uuid", goalId);
        mvc.perform(get("/api/v1/study-sessions/" + id).session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalId").value((Object) null))
                .andExpect(jsonPath("$.data.subject").value("Lịch sử"))
                .andExpect(jsonPath("$.data.focusedSeconds").value(90));
        // The task is gone, so a new session cannot start for it.
        start(s, taskId, key()).andExpect(status().isNotFound());
    }

    @Test
    void completedTasksCanStillBeReviewedAndAccountDeletionRemovesSessions() throws Exception {
        var s = login("a@example.com");
        String goalId = goal(s, "Ôn lại");
        String taskId = task(goalId, "Đã xong");
        jdbc.update("UPDATE study_tasks SET completed=true WHERE id=?::uuid", taskId);
        start(s, taskId, key()).andExpect(status().isCreated());
        assertEquals(1, sessionRows());
        jdbc.update("DELETE FROM app_users");
        assertEquals(0, sessionRows());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM study_session_receipts", Integer.class));
    }

    @Test
    void historyIsNewestFirstPagedAndFilterableByTask() throws Exception {
        var s = login("a@example.com");
        String goalId = goal(s, "Toán");
        String t1 = task(goalId, "Một");
        String t2 = task(goalId, "Hai");
        for (String task : List.of(t1, t2, t1)) {
            String id = data(start(s, task, key()).andExpect(status().isCreated())).get("id").asText();
            clock.advance(60);
            act(s, id, "FINISH", 0, key()).andExpect(status().isOk());
            clock.advance(60);
        }
        JsonNode page1 = mapper.readTree(mvc.perform(get("/api/v1/study-sessions?page=1&limit=2").session(s))
                .andExpect(jsonPath("$.meta.totalItems").value(3)).andExpect(jsonPath("$.meta.totalPages").value(2))
                .andReturn().getResponse().getContentAsString());
        assertEquals(2, page1.get("data").size());
        Instant first = Instant.parse(page1.get("data").get(0).get("startedAt").asText());
        Instant second = Instant.parse(page1.get("data").get(1).get("startedAt").asText());
        assertTrue(first.isAfter(second));
        mvc.perform(get("/api/v1/study-sessions?page=2&limit=2").session(s)).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/study-sessions?taskId=" + t1).session(s)).andExpect(jsonPath("$.meta.totalItems").value(2));
        mvc.perform(get("/api/v1/study-sessions?taskId=" + t2).session(s)).andExpect(jsonPath("$.meta.totalItems").value(1));
    }

    @Test
    void validationAuthAndCsrfAreEnforcedAndLeaveTheDatabaseUntouched() throws Exception {
        var s = login("a@example.com");
        String taskId = task(goal(s, "Toán"), "Validate");

        mvc.perform(post("/api/v1/study-sessions").with(csrf()).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":\"" + taskId + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/study-sessions").session(s).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":\"" + taskId + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/study-sessions").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\"" + taskId + "\"}")).andExpect(status().isBadRequest());
        start(s, taskId, "nope").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/study-sessions").session(s).with(csrf()).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":\"" + taskId + "\",\"userId\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/study-sessions").session(s).with(csrf()).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{bad json")).andExpect(status().isBadRequest());
        assertEquals(0, sessionRows());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM study_session_receipts", Integer.class));

        String id = data(start(s, taskId, key()).andExpect(status().isCreated())).get("id").asText();
        mvc.perform(post("/api/v1/study-sessions/" + id + "/actions").session(s).with(csrf()).header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"FINISH\",\"expectedVersion\":0,\"elapsedSeconds\":1500}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT accumulated_focus_seconds FROM study_sessions WHERE id=?::uuid", Integer.class, id));
        assertTrue(jdbc.queryForObject("SELECT ended_at IS NULL FROM study_sessions WHERE id=?::uuid", Boolean.class, id));
    }

    @Test
    void taskTotalsAccumulateOnlyCompletedSessionsWithoutChangingEstimates() throws Exception {
        var owner = login("totals@example.test");
        String goalId = goal(owner, "Totals"), taskId = task(goalId, "Study");
        for (int n=0; n<2; n++) {
            String id = data(start(owner, taskId, key()).andExpect(status().isCreated())).path("id").asText();
            clock.advance(1200);
            act(owner,id,"FINISH",0,key()).andExpect(status().isOk());
        }
        String cancelled=data(start(owner,taskId,key()).andExpect(status().isCreated())).path("id").asText();
        clock.advance(300); act(owner,cancelled,"CANCEL",0,key()).andExpect(status().isOk());
        String running=data(start(owner,taskId,key()).andExpect(status().isCreated())).path("id").asText();
        clock.advance(100);
        String url="/api/v1/study-sessions/task-totals?taskIds="+taskId;
        mvc.perform(get(url).session(owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].totalFocusSeconds").value(2400))
            .andExpect(jsonPath("$.data[0].completedSessionCount").value(2));
        assertEquals(45,jdbc.queryForObject("SELECT estimated_minutes FROM study_tasks WHERE id=?::uuid",Integer.class,taskId));
        assertFalse(jdbc.queryForObject("SELECT completed FROM study_tasks WHERE id=?::uuid",Boolean.class,taskId));
        var other=login("totals-other@example.test");
        mvc.perform(get(url).session(other)).andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/study-sessions/task-totals?taskIds=invalid").session(owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/study-sessions/task-totals?taskIds="+String.join(",",java.util.Collections.nCopies(101,taskId))).session(owner)).andExpect(status().isBadRequest());
        act(owner,running,"CANCEL",0,key()).andExpect(status().isOk());
    }

    @Test
    void changingEstimateChecksSharedBudgetAndPreservesStudiedTime() throws Exception {
        var owner=login("estimate@example.test");
        String goalId=goal(owner,"Estimate"),taskId=task(goalId,"Study");
        jdbc.update("UPDATE app_users SET study_daily_minutes=60 WHERE email=?","estimate@example.test");
        String id=data(start(owner,taskId,key()).andExpect(status().isCreated())).path("id").asText();
        clock.advance(1200); act(owner,id,"FINISH",0,key()).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/tasks/"+taskId).session(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"estimatedMinutes\":40}" )).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/tasks/"+taskId).session(owner).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"estimatedMinutes\":61}" )).andExpect(status().isUnprocessableEntity());
        assertEquals(40,jdbc.queryForObject("SELECT estimated_minutes FROM study_tasks WHERE id=?::uuid",Integer.class,taskId));
        mvc.perform(get("/api/v1/study-sessions/task-totals?taskIds="+taskId).session(owner)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].totalFocusSeconds").value(1200));
    }
}
