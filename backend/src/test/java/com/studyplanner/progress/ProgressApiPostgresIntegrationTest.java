package com.studyplanner.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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
 * Progress over real HTTP, session auth and PostgreSQL. Only for a disposable database:
 * mvn -f backend/pom.xml verify -Dgoal.integration=true
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "goal.integration", matches = "true")
class ProgressApiPostgresIntegrationTest {
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

    private void addTasks(String goalId, int total, int completed) {
        for (int i = 0; i < total; i++) {
            jdbc.update("INSERT INTO study_tasks (goal_id, title, scheduled_date, estimated_minutes, completed) "
                    + "VALUES (?::uuid, ?, ?, 5, ?)", goalId, "Việc " + i, today.plusDays(1 + i % 20), i < completed);
        }
    }

    private String progress(String goalId) {
        return "/api/v1/goals/" + goalId + "/progress";
    }

    @Test
    @DisplayName("a goal without tasks is 0/0 with rate 0")
    void emptyGoalIsZeroOfZero() throws Exception {
        var s = registerAndLogin("a-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);

        mvc.perform(get(progress(goal)).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalId").value(goal))
                .andExpect(jsonPath("$.data.totalTasks").value(0))
                .andExpect(jsonPath("$.data.completedTasks").value(0))
                .andExpect(jsonPath("$.data.remainingTasks").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(0.0));
    }

    @Test
    @DisplayName("counts follow the stored tasks, over the whole goal and not one page, and track changes")
    void countsTheWholeGoalAndTracksChanges() throws Exception {
        var s = registerAndLogin("b-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        addTasks(goal, 25, 10); // 25 tasks is more than one page of 20

        mvc.perform(get(progress(goal)).session(s))
                .andExpect(jsonPath("$.data.totalTasks").value(25))
                .andExpect(jsonPath("$.data.completedTasks").value(10))
                .andExpect(jsonPath("$.data.remainingTasks").value(15))
                .andExpect(jsonPath("$.data.completionRate").value(0.4));

        jdbc.update("UPDATE study_tasks SET completed = true WHERE goal_id = ?::uuid", goal);
        mvc.perform(get(progress(goal)).session(s))
                .andExpect(jsonPath("$.data.completedTasks").value(25))
                .andExpect(jsonPath("$.data.remainingTasks").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(1.0));

        jdbc.update("UPDATE study_tasks SET completed = false WHERE goal_id = ?::uuid", goal); // re-open all
        mvc.perform(get(progress(goal)).session(s))
                .andExpect(jsonPath("$.data.completedTasks").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(0.0));

        jdbc.update("DELETE FROM study_tasks WHERE goal_id = ?::uuid", goal);
        mvc.perform(get(progress(goal)).session(s))
                .andExpect(jsonPath("$.data.totalTasks").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(0.0));
    }

    @Test
    @DisplayName("only the goal's own tasks count; other goals and plan drafts do not")
    void countsOnlyThisGoalsTasksNotDrafts() throws Exception {
        var s = registerAndLogin("c-" + UUID.randomUUID() + "@example.test");
        String goalOne = createGoal(s);
        String goalTwo = createGoal(s);
        addTasks(goalOne, 3, 1);
        addTasks(goalTwo, 7, 7);
        jdbc.update("INSERT INTO plan_drafts (goal_id, goal_version, proposal, expires_at) "
                + "VALUES (?::uuid, 0, '{\"tasks\":[{\"title\":\"a\"},{\"title\":\"b\"}]}'::jsonb, now() + interval '1 day')",
                goalOne);

        mvc.perform(get(progress(goalOne)).session(s))
                .andExpect(jsonPath("$.data.totalTasks").value(3))
                .andExpect(jsonPath("$.data.completedTasks").value(1));
        mvc.perform(get(progress(goalTwo)).session(s))
                .andExpect(jsonPath("$.data.totalTasks").value(7))
                .andExpect(jsonPath("$.data.completionRate").value(1.0));
    }

    @Test
    @DisplayName("another account gets 404 and learns nothing; no session is 401; reading does not change the goal")
    void ownershipAndAuthentication() throws Exception {
        var a = registerAndLogin("d-" + UUID.randomUUID() + "@example.test");
        var b = registerAndLogin("e-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(a);
        addTasks(goal, 4, 2);

        mvc.perform(get(progress(goal)).session(b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get(progress(goal))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/goals/" + UUID.randomUUID() + "/progress").session(a))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/goals/not-a-uuid/progress").session(a)).andExpect(status().isNotFound());

        mvc.perform(get(progress(goal)).session(a)).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT version FROM study_goals WHERE id = ?::uuid", Integer.class, goal));
    }

    @Test
    @DisplayName("a deleted goal has no progress (404) and the tasks are gone with it")
    void deletedGoalHasNoProgress() throws Exception {
        var s = registerAndLogin("f-" + UUID.randomUUID() + "@example.test");
        String goal = createGoal(s);
        addTasks(goal, 2, 1);
        mvc.perform(get(progress(goal)).session(s)).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/goals/" + goal).session(s).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(progress(goal)).session(s)).andExpect(status().isNotFound());
    }
}
