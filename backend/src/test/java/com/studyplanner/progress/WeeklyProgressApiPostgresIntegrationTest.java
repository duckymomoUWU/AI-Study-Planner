package com.studyplanner.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

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
 * Weekly progress over real HTTP, session auth and PostgreSQL. Only for a disposable database:
 * mvn -f backend/pom.xml verify -Dgoal.integration=true
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "goal.integration", matches = "true")
class WeeklyProgressApiPostgresIntegrationTest {
    private static final String URL = "/api/v1/progress/weekly";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;

    private LocalDate today;
    private LocalDate monday; // Monday of the week that holds "today"

    @BeforeEach
    void clean() {
        assertEquals("study_planner_test", jdbc.queryForObject("SELECT current_database()", String.class),
                "Weekly integration tests must use the disposable study_planner_test database");
        jdbc.update("DELETE FROM app_users");
        today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private MockHttpSession registerAndLogin(String email) throws Exception {
        String creds = "{\"email\":\"" + email + "\",\"password\":\"Password123!\"}";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(creds))
                .andExpect(status().isCreated());
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(creds)).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String createGoal(MockHttpSession s) throws Exception {
        String body = "{\"subject\":\"Toán\",\"topics\":\"Tích phân\",\"startDate\":\"" + today
                + "\",\"examDate\":\"" + today.plusDays(80) + "\",\"minutesPerDay\":120}";
        String res = mvc.perform(post("/api/v1/goals").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var node = mapper.readTree(res);
        return (node.has("data") ? node.get("data") : node).get("id").asText();
    }

    private void addTask(String goalId, LocalDate date, boolean completed) {
        jdbc.update("INSERT INTO study_tasks (goal_id, title, scheduled_date, estimated_minutes, completed) "
                + "VALUES (?::uuid, 'Việc', ?, 10, ?)", goalId, date, completed);
    }

    @Test
    @DisplayName("a week without tasks is 0/0 with rate 0 and seven zero days, not an error")
    void emptyWeekIsZeroOfZero() throws Exception {
        var s = registerAndLogin("a-" + UUID.randomUUID() + "@example.test");
        createGoal(s);

        mvc.perform(get(URL + "?date=" + monday.plusWeeks(5)).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value(monday.plusWeeks(5).toString()))
                .andExpect(jsonPath("$.data.totalTasks").value(0))
                .andExpect(jsonPath("$.data.completedTasks").value(0))
                .andExpect(jsonPath("$.data.remainingTasks").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(0.0))
                .andExpect(jsonPath("$.data.days.length()").value(7));
    }

    @Test
    @DisplayName("counts the tasks of all goals in the week; Monday and Sunday are in, the neighbours are out")
    void countsTheWeekAcrossGoalsWithInclusiveBoundaries() throws Exception {
        var s = registerAndLogin("b-" + UUID.randomUUID() + "@example.test");
        String goalOne = createGoal(s);
        String goalTwo = createGoal(s);
        addTask(goalOne, monday, true);                  // Monday: in
        addTask(goalOne, monday.plusDays(2), false);
        addTask(goalTwo, monday.plusDays(2), true);      // same day, other goal
        addTask(goalTwo, monday.plusDays(6), true);      // Sunday: in
        addTask(goalOne, monday.minusDays(1), true);     // previous Sunday: out
        addTask(goalTwo, monday.plusDays(7), false);     // next Monday: out

        mvc.perform(get(URL + "?date=" + monday).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value(monday.toString()))
                .andExpect(jsonPath("$.data.weekEnd").value(monday.plusDays(6).toString()))
                .andExpect(jsonPath("$.data.totalTasks").value(4))
                .andExpect(jsonPath("$.data.completedTasks").value(3))
                .andExpect(jsonPath("$.data.remainingTasks").value(1))
                .andExpect(jsonPath("$.data.completionRate").value(0.75))
                .andExpect(jsonPath("$.data.days[0].totalTasks").value(1))
                .andExpect(jsonPath("$.data.days[0].completedTasks").value(1))
                .andExpect(jsonPath("$.data.days[1].totalTasks").value(0))
                .andExpect(jsonPath("$.data.days[2].totalTasks").value(2))
                .andExpect(jsonPath("$.data.days[2].completedTasks").value(1))
                .andExpect(jsonPath("$.data.days[6].totalTasks").value(1));

        // Previous and next week hold only their own neighbour task.
        mvc.perform(get(URL + "?date=" + monday.minusWeeks(1)).session(s))
                .andExpect(jsonPath("$.data.totalTasks").value(1))
                .andExpect(jsonPath("$.data.completedTasks").value(1))
                .andExpect(jsonPath("$.data.days[6].totalTasks").value(1));
        mvc.perform(get(URL + "?date=" + monday.plusWeeks(1)).session(s))
                .andExpect(jsonPath("$.data.totalTasks").value(1))
                .andExpect(jsonPath("$.data.completedTasks").value(0))
                .andExpect(jsonPath("$.data.days[0].totalTasks").value(1));
    }

    @Test
    @DisplayName("any date of the week gives the same answer; no date means the current week")
    void anyDayOfTheWeekAndTheDefaultWeek() throws Exception {
        var s = registerAndLogin("c-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        addTask(goal, today, true);
        addTask(goal, monday.plusDays(3), false);

        for (int i = 0; i < 7; i++) {
            mvc.perform(get(URL + "?date=" + monday.plusDays(i)).session(s))
                    .andExpect(jsonPath("$.data.weekStart").value(monday.toString()))
                    .andExpect(jsonPath("$.data.totalTasks").value(today.equals(monday.plusDays(3)) ? 2 : 2));
        }
        mvc.perform(get(URL).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value(monday.toString()))
                .andExpect(jsonPath("$.data.completedTasks").value(1));
    }

    @Test
    @DisplayName("the weeks of a year boundary are calendar weeks")
    void yearBoundaryWeek() throws Exception {
        var s = registerAndLogin("d-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        addTask(goal, LocalDate.of(2026, 12, 31), true);
        addTask(goal, LocalDate.of(2027, 1, 1), false);
        addTask(goal, LocalDate.of(2027, 1, 4), false); // next Monday

        mvc.perform(get(URL + "?date=2027-01-02").session(s))
                .andExpect(jsonPath("$.data.weekStart").value("2026-12-28"))
                .andExpect(jsonPath("$.data.weekEnd").value("2027-01-03"))
                .andExpect(jsonPath("$.data.totalTasks").value(2))
                .andExpect(jsonPath("$.data.completedTasks").value(1));
    }

    @Test
    @DisplayName("another account's tasks are never counted; no session is 401")
    void ownershipAndAuthentication() throws Exception {
        var a = registerAndLogin("e-" + UUID.randomUUID() + "@example.test");
        var b = registerAndLogin("f-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(a);
        addTask(goal, monday.plusDays(1), true);
        addTask(goal, monday.plusDays(1), false);

        mvc.perform(get(URL + "?date=" + monday).session(a))
                .andExpect(jsonPath("$.data.totalTasks").value(2));
        mvc.perform(get(URL + "?date=" + monday).session(b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTasks").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(0.0));
        mvc.perform(get(URL + "?date=" + monday)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("plan drafts are not tasks; reading does not change the goal; deleting the goal empties the week")
    void draftsAreIgnoredAndReadsAreSideEffectFree() throws Exception {
        var s = registerAndLogin("g-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        addTask(goal, monday.plusDays(1), true);
        jdbc.update("INSERT INTO plan_drafts (goal_id, goal_version, proposal, expires_at) "
                + "VALUES (?::uuid, 0, '{\"tasks\":[{\"title\":\"a\"},{\"title\":\"b\"}]}'::jsonb, now() + interval '1 day')",
                goal);
        int versionBefore = jdbc.queryForObject("SELECT version FROM study_goals WHERE id = ?::uuid", Integer.class, goal);

        mvc.perform(get(URL + "?date=" + monday).session(s))
                .andExpect(jsonPath("$.data.totalTasks").value(1));
        assertEquals(versionBefore,
                jdbc.queryForObject("SELECT version FROM study_goals WHERE id = ?::uuid", Integer.class, goal));

        jdbc.update("DELETE FROM study_goals WHERE id = ?::uuid", goal);
        mvc.perform(get(URL + "?date=" + monday).session(s))
                .andExpect(jsonPath("$.data.totalTasks").value(0));
    }

    @Test
    @DisplayName("an invalid date is a 400 with field details; the supported range is 2000 to 2100")
    void invalidAndOutOfRangeDates() throws Exception {
        var s = registerAndLogin("h-" + UUID.randomUUID() + "@example.test");

        mvc.perform(get(URL + "?date=10/11/2026").session(s))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("date"))
                .andExpect(jsonPath("$.error.details[0].issue").value("invalid_date"));
        mvc.perform(get(URL + "?date=1999-12-31").session(s))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].issue").value("out_of_range"));
        mvc.perform(get(URL + "?date=2101-01-01").session(s)).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "?date=2000-01-01").session(s)).andExpect(status().isOk());
        mvc.perform(get(URL + "?date=2100-12-31").session(s)).andExpect(status().isOk());
    }
}
