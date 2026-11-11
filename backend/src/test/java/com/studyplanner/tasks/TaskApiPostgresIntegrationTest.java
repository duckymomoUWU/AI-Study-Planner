package com.studyplanner.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mark-complete over real HTTP, session auth and PostgreSQL. Only for a disposable database:
 * mvn -f backend/pom.xml verify -Dgoal.integration=true
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "goal.integration", matches = "true")
class TaskApiPostgresIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;

    private LocalDate today;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM app_users");
        today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private MockHttpSession registerAndLogin(String email) throws Exception {
        String creds = "{\"email\":\"" + email + "\",\"password\":\"Password123!\"}";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(creds))
                .andExpect(status().isCreated());
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(creds)).andExpect(status().isOk()).andReturn();
        // This suite tests per-goal behavior; explicitly configure a non-binding shared fixture budget.
        jdbc.update("UPDATE app_users SET study_daily_minutes=1440 WHERE email=?",email);
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String createGoal(MockHttpSession s) throws Exception {
        String body = "{\"subject\":\"Toán\",\"topics\":\"Tích phân\",\"startDate\":\"" + today
                + "\",\"examDate\":\"" + today.plusDays(30) + "\",\"minutesPerDay\":120}";
        String res = mvc.perform(post("/api/v1/goals").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("data").get("id").asText();
    }

    private String addTask(String goalId, LocalDate date, int minutes, String title) {
        return jdbc.queryForObject("INSERT INTO study_tasks (goal_id, title, scheduled_date, estimated_minutes) "
                + "VALUES (?::uuid, ?, ?, ?) RETURNING id::text", String.class, goalId, title, date, minutes);
    }

    private boolean completedInDb(String taskId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT completed FROM study_tasks WHERE id = ?::uuid",
                Boolean.class, taskId));
    }

    private int versionInDb(String goalId) {
        return jdbc.queryForObject("SELECT version FROM study_goals WHERE id = ?::uuid", Integer.class, goalId);
    }

    private String url(String goalId, String taskId) {
        return "/api/v1/goals/" + goalId + "/tasks/" + taskId;
    }

    private org.springframework.test.web.servlet.ResultActions setCompleted(MockHttpSession s, String goalId,
                                                                           String taskId, boolean value)
            throws Exception {
        return mvc.perform(patch(url(goalId, taskId)).session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":" + value + "}"));
    }

    @Test
    @DisplayName("mark done, repeat, re-open: stored state and goal version follow the rules")
    void markDoneRepeatAndReopen() throws Exception {
        var s = registerAndLogin("a-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        String task = addTask(goal, today.plusDays(1), 30, "Ôn SQL");
        assertEquals(0, versionInDb(goal));

        setCompleted(s, goal, task, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(task))
                .andExpect(jsonPath("$.data.goalId").value(goal))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.source").value("manual"))
                .andExpect(jsonPath("$.data.estimatedMinutes").value(30));
        assertEquals(true, completedInDb(task));
        assertEquals(1, versionInDb(goal));

        // Sending the same target state again changes nothing and does not touch the version.
        setCompleted(s, goal, task, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
        assertEquals(true, completedInDb(task));
        assertEquals(1, versionInDb(goal));

        setCompleted(s, goal, task, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(false));
        assertEquals(false, completedInDb(task));
        assertEquals(2, versionInDb(goal));
        setCompleted(s, goal, task, false).andExpect(status().isOk());
        assertEquals(2, versionInDb(goal));
    }

    @Test
    @DisplayName("the day view and the goal list show the stored state, also after a reload; filters work")
    void listsReflectTheStoredState() throws Exception {
        var s = registerAndLogin("b-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        LocalDate day = today.plusDays(2);
        String done = addTask(goal, day, 30, "Đã làm");
        addTask(goal, day, 45, "Chưa làm");
        addTask(goal, day.plusDays(1), 20, "Ngày khác");
        setCompleted(s, goal, done, true).andExpect(status().isOk());

        mvc.perform(get("/api/v1/tasks?date=" + day).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.totalItems").value(2))
                .andExpect(jsonPath("$.data[?(@.title=='Đã làm')].completed").value(true))
                .andExpect(jsonPath("$.data[?(@.title=='Chưa làm')].completed").value(false));
        mvc.perform(get("/api/v1/goals/" + goal + "/tasks?completed=true").session(s))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(done));
        mvc.perform(get("/api/v1/goals/" + goal + "/tasks?completed=false&date=" + day).session(s))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Chưa làm"));
        mvc.perform(get(url(goal, done)).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
        mvc.perform(get("/api/v1/tasks?date=" + today.plusDays(9)).session(s))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    @DisplayName("account B cannot read or change account A's task; nothing is written")
    void crossAccountAccessIsRejected() throws Exception {
        var a = registerAndLogin("c-" + UUID.randomUUID() + "@example.test");
        var b = registerAndLogin("d-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(a);
        String task = addTask(goal, today.plusDays(1), 30, "Việc của A");

        setCompleted(b, goal, task, true).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get(url(goal, task)).session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/goals/" + goal + "/tasks").session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tasks?date=" + today.plusDays(1)).session(b))
                .andExpect(jsonPath("$.data").isEmpty());

        assertEquals(false, completedInDb(task));
        assertEquals(0, versionInDb(goal));
    }

    @Test
    @DisplayName("a task ID under the wrong goal is 404, even for the same owner")
    void taskMustBelongToTheGoalInTheUrl() throws Exception {
        var s = registerAndLogin("e-" + UUID.randomUUID() + "@example.test");
        String goalOne = createGoal(s);
        String goalTwo = createGoal(s);
        String task = addTask(goalOne, today.plusDays(1), 30, "Của goal một");

        setCompleted(s, goalTwo, task, true).andExpect(status().isNotFound());
        mvc.perform(get(url(goalTwo, task)).session(s)).andExpect(status().isNotFound());
        assertEquals(false, completedInDb(task));
        assertEquals(0, versionInDb(goalOne));
        assertEquals(0, versionInDb(goalTwo));
    }

    @Test
    @DisplayName("no session is 401, no CSRF token is 403, bad bodies and dates are 400; nothing is written")
    void authCsrfAndValidation() throws Exception {
        var s = registerAndLogin("f-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        String task = addTask(goal, today.plusDays(1), 30, "Ôn");

        mvc.perform(patch(url(goal, task)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(patch(url(goal, task)).session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        for (String bad : List.of("{}", "{\"completed\":null}", "{\"completed\":\"true\"}", "{\"completed\":1}",
                "{\"completed\":true,\"title\":null}", "{\"completed\":true,\"goalId\":\"x\"}")) {
            mvc.perform(patch(url(goal, task)).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get("/api/v1/tasks").session(s)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tasks?date=not-a-date").session(s)).andExpect(status().isBadRequest());

        assertEquals(false, completedInDb(task));
        assertEquals(0, versionInDb(goal));
    }

    @Test
    @DisplayName("two simultaneous requests for the same change store it once and bump the version once")
    void simultaneousRequestsAreSerialized() throws Exception {
        var s = registerAndLogin("g-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        String task = addTask(goal, today.plusDays(1), 30, "Ôn");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> setCompleted(s, goal, task, true).andReturn().getResponse().getStatus();
            Future<Integer> one = pool.submit(call);
            Future<Integer> two = pool.submit(call);
            assertEquals(200, one.get());
            assertEquals(200, two.get());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(true, completedInDb(task));
        assertEquals(1, versionInDb(goal));
    }

    @Test
    @DisplayName("finishing a task moves the goal version: an edit with the old version is a 409 until reloaded")
    void goalEditNeedsTheNewVersionAfterATaskChange() throws Exception {
        var s = registerAndLogin("h-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        String task = addTask(goal, today.plusDays(1), 30, "Ôn");
        setCompleted(s, goal, task, true).andExpect(status().isOk());

        mvc.perform(patch("/api/v1/goals/" + goal).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutesPerDay\":90,\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOAL_VERSION_CONFLICT"));
        mvc.perform(patch("/api/v1/goals/" + goal).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutesPerDay\":90,\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));
        assertEquals(true, completedInDb(task));
    }

    @Test
    @DisplayName("deleting the goal removes its tasks, so a completed task is no longer reachable")
    void deletedGoalTasksAreGone() throws Exception {
        var s = registerAndLogin("i-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        String task = addTask(goal, today.plusDays(1), 30, "Ôn");
        setCompleted(s, goal, task, true).andExpect(status().isOk());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/goals/" + goal).session(s).with(csrf()))
                .andExpect(status().isNoContent());
        setCompleted(s, goal, task, false).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM study_tasks WHERE id = ?::uuid", Integer.class, task));
    }
}
