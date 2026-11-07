package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Real HTTP + session auth + PostgreSQL. Only for a disposable database:
 * mvn -f backend/pom.xml verify -Dgoal.integration=true
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "goal.integration", matches = "true")
class GoalApiPostgresIntegrationTest {
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
                        .content(creds))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String body(String subject, int minutes, LocalDate start, LocalDate exam) {
        return "{\"subject\":\"" + subject + "\",\"topics\":\"Tích phân\",\"startDate\":\"" + start
                + "\",\"examDate\":\"" + exam + "\",\"minutesPerDay\":" + minutes + "}";
    }

    private JsonNode create(MockHttpSession s, String json) throws Exception {
        String res = mvc.perform(post("/api/v1/goals").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("data");
    }

    @Test
    @DisplayName("create then list and read back from the database; trimmed text; owner from session")
    void createListAndReadPersist() throws Exception {
        var s = registerAndLogin("a-" + UUID.randomUUID() + "@example.test");
        JsonNode created = create(s, body("  Giải tích  ", 90, today, today.plusDays(30)));
        assertEquals("Giải tích", created.get("subject").asText());
        assertEquals(0, created.get("version").asInt());

        mvc.perform(get("/api/v1/goals").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(created.get("id").asText()));
        mvc.perform(get("/api/v1/goals/" + created.get("id").asText()).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("Giải tích"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Ho_Chi_Minh"));

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM study_goals", Integer.class);
        assertEquals(1, rows);
    }

    @Test
    @DisplayName("owner comes from the session; a client-supplied userId is rejected and nothing is stored")
    void ownerComesFromSession() throws Exception {
        String email = "b-" + UUID.randomUUID() + "@example.test";
        var s = registerAndLogin(email);
        UUID other = UUID.randomUUID();
        String json = body("Toán", 60, today, today.plusDays(10)).replaceFirst("\\{", "{\"userId\":\"" + other + "\",");

        mvc.perform(post("/api/v1/goals").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='userId' && @.issue=='unknown_field')]").exists());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM study_goals", Integer.class));

        create(s, body("Toán", 60, today, today.plusDays(10)));
        Integer mine = jdbc.queryForObject(
                "SELECT count(*) FROM study_goals g JOIN app_users u ON u.id = g.user_id WHERE u.email = ?",
                Integer.class, email);
        assertEquals(1, mine);
    }

    @Test
    @DisplayName("another account cannot read a goal by ID or see it in the list")
    void crossAccountAccessIsRejected() throws Exception {
        var a = registerAndLogin("c-" + UUID.randomUUID() + "@example.test");
        var b = registerAndLogin("d-" + UUID.randomUUID() + "@example.test");
        JsonNode goal = create(a, body("Toán", 60, today, today.plusDays(10)));

        mvc.perform(get("/api/v1/goals/" + goal.get("id").asText()).session(b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/goals").session(b))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/goals/" + goal.get("id").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("boundaries: 15/480 minutes and 90 days accepted; 14/481, 91 days, blanks, past exam rejected")
    void validationBoundaries() throws Exception {
        var s = registerAndLogin("e-" + UUID.randomUUID() + "@example.test");
        create(s, body("Toán", 15, today, today.plusDays(10)));
        create(s, body("Toán", 480, today, today.plusDays(90)));

        assertRejected(s, body("Toán", 14, today, today.plusDays(10)), "minutesPerDay", "out_of_range");
        assertRejected(s, body("Toán", 481, today, today.plusDays(10)), "minutesPerDay", "out_of_range");
        assertRejected(s, body("Toán", 60, today, today.plusDays(91)), "examDate", "exceeds_90_days");
        assertRejected(s, body("Toán", 60, today, today), "examDate", "must_be_after_start_date");
        assertRejected(s, body("Toán", 60, today.minusDays(20), today.minusDays(1)), "examDate", "must_not_be_in_past");
        assertRejected(s, body("   ", 60, today, today.plusDays(10)), "subject", "required");
        assertRejected(s, body("Toán", 60, today, today.plusDays(10)).replace("Tích phân", "  "),
                "topics", "required");

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM study_goals", Integer.class);
        assertEquals(2, rows);
    }

    private void assertRejected(MockHttpSession s, String json, String field, String code) throws Exception {
        mvc.perform(post("/api/v1/goals").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='" + field + "' && @.issue=='" + code + "')]").exists());
    }

    private String patchBody(String subject, int minutes, LocalDate start, LocalDate exam, int version) {
        String base = body(subject, minutes, start, exam);
        return base.substring(0, base.length() - 1) + ",\"expectedVersion\":" + version + "}";
    }

    private void addTask(String goalId, LocalDate date, int minutes) {
        jdbc.update("INSERT INTO study_tasks (goal_id, title, scheduled_date, estimated_minutes) "
                + "VALUES (?::uuid, ?, ?, ?)", goalId, "Task " + date, date, minutes);
    }

    @Test
    @DisplayName("PUT: valid edit is saved and read back; version increments; stale version is 409")
    void updatePersistsAndChecksVersion() throws Exception {
        var s = registerAndLogin("f-" + UUID.randomUUID() + "@example.test");
        JsonNode goal = create(s, body("Toán", 60, today, today.plusDays(30)));
        String id = goal.get("id").asText();

        mvc.perform(patch("/api/v1/goals/" + id).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("  Toán rời rạc ", 120, today, today.plusDays(45), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("Toán rời rạc"))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(get("/api/v1/goals/" + id).session(s))
                .andExpect(jsonPath("$.data.minutesPerDay").value(120))
                .andExpect(jsonPath("$.data.examDate").value(today.plusDays(45).toString()));

        mvc.perform(patch("/api/v1/goals/" + id).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("Cũ", 30, today, today.plusDays(45), 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOAL_VERSION_CONFLICT"));
        mvc.perform(get("/api/v1/goals/" + id).session(s)).andExpect(jsonPath("$.data.subject").value("Toán rời rạc"));
    }

    @Test
    @DisplayName("PUT: tasks past the new exam date or over the new budget -> 409 with day details, nothing saved")
    void updateRejectedByExistingTasksIsAtomic() throws Exception {
        var s = registerAndLogin("g-" + UUID.randomUUID() + "@example.test");
        JsonNode goal = create(s, body("Toán", 120, today, today.plusDays(30)));
        String id = goal.get("id").asText();
        LocalDate busy = today.plusDays(3);
        addTask(id, busy, 70);
        addTask(id, busy, 40);
        addTask(id, today.plusDays(20), 30);

        mvc.perform(patch("/api/v1/goals/" + id).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("Toán", 100, today, today.plusDays(15), 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GOAL_UPDATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[?(@.issue=='task_on_or_after_exam_date')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString(today.plusDays(20).toString()))))
                .andExpect(jsonPath("$.error.details[?(@.issue=='daily_budget_exceeded')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString(busy.toString()))));

        mvc.perform(get("/api/v1/goals/" + id).session(s))
                .andExpect(jsonPath("$.data.minutesPerDay").value(120))
                .andExpect(jsonPath("$.data.examDate").value(today.plusDays(30).toString()))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    @DisplayName("PUT: another account gets 404 and the goal is unchanged; invalid input is 400")
    void updateCrossAccountAndInvalidInpatch() throws Exception {
        var a = registerAndLogin("h-" + UUID.randomUUID() + "@example.test");
        var b = registerAndLogin("i-" + UUID.randomUUID() + "@example.test");
        JsonNode goal = create(a, body("Toán", 60, today, today.plusDays(30)));
        String id = goal.get("id").asText();

        mvc.perform(patch("/api/v1/goals/" + id).session(b).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("Hack", 200, today, today.plusDays(30), 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/goals/" + id).session(a)).andExpect(jsonPath("$.data.subject").value("Toán"));

        mvc.perform(patch("/api/v1/goals/" + id).session(a).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("Toán", 481, today, today.plusDays(91), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='minutesPerDay')]").exists())
                .andExpect(jsonPath("$.error.details[?(@.field=='examDate' && @.issue=='exceeds_90_days')]").exists());
    }

    private int rows(String table, String goalId) {
        if (!table.equals("study_goals") && !table.equals("study_tasks") && !table.equals("plan_drafts")) {
            throw new IllegalArgumentException(table);
        }
        String column = table.equals("study_goals") ? "id" : "goal_id";
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?::uuid",
                Integer.class, goalId);
    }

    private void addDraft(String goalId) {
        jdbc.update("INSERT INTO plan_drafts (goal_id, goal_version, proposal, expires_at) "
                + "VALUES (?::uuid, 0, '{\"tasks\":[]}'::jsonb, now() + interval '1 day')", goalId);
    }

    @Test
    @DisplayName("DELETE: removes the goal with its tasks and drafts; other goals are untouched; repeat is 404")
    void deleteRemovesGoalTasksAndDraftsOnly() throws Exception {
        var s = registerAndLogin("j-" + UUID.randomUUID() + "@example.test");
        String doomed = create(s, body("Toán", 60, today, today.plusDays(30))).get("id").asText();
        String kept = create(s, body("Lý", 60, today, today.plusDays(30))).get("id").asText();
        addTask(doomed, today.plusDays(1), 30);
        addTask(doomed, today.plusDays(2), 30);
        addDraft(doomed);
        addTask(kept, today.plusDays(1), 30);
        addDraft(kept);
        assertEquals(1, rows("study_goals", doomed));
        assertEquals(2, rows("study_tasks", doomed));
        assertEquals(1, rows("plan_drafts", doomed));

        mvc.perform(delete("/api/v1/goals/" + doomed).session(s).with(csrf())).andExpect(status().isNoContent());

        assertEquals(0, rows("study_goals", doomed));
        assertEquals(0, rows("study_tasks", doomed));
        assertEquals(0, rows("plan_drafts", doomed));
        assertEquals(1, rows("study_goals", kept));
        assertEquals(1, rows("study_tasks", kept));
        assertEquals(1, rows("plan_drafts", kept));
        mvc.perform(get("/api/v1/goals/" + doomed).session(s)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/goals/" + doomed).session(s).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/goals").session(s)).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("DELETE: another account gets 404 and nothing is deleted; no session is 401")
    void deleteCrossAccountAndUnauthenticated() throws Exception {
        var a = registerAndLogin("k-" + UUID.randomUUID() + "@example.test");
        var b = registerAndLogin("l-" + UUID.randomUUID() + "@example.test");
        String id = create(a, body("Toán", 60, today, today.plusDays(30))).get("id").asText();
        addTask(id, today.plusDays(1), 30);
        addDraft(id);

        mvc.perform(delete("/api/v1/goals/" + id).session(b).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(delete("/api/v1/goals/" + id).with(csrf())).andExpect(status().isUnauthorized());

        assertEquals(1, rows("study_goals", id));
        assertEquals(1, rows("study_tasks", id));
        assertEquals(1, rows("plan_drafts", id));
        mvc.perform(get("/api/v1/goals/" + id).session(a)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("list is paged (data + meta), newest first, and limited to the caller")
    void listIsPagedWithMeta() throws Exception {
        var a = registerAndLogin("p-" + UUID.randomUUID() + "@example.test");
        var other = registerAndLogin("q-" + UUID.randomUUID() + "@example.test");
        String first = create(a, body("Một", 60, today, today.plusDays(10))).get("id").asText();
        String second = create(a, body("Hai", 60, today, today.plusDays(10))).get("id").asText();
        String third = create(a, body("Ba", 60, today, today.plusDays(10))).get("id").asText();
        create(other, body("Của người khác", 60, today, today.plusDays(10)));

        mvc.perform(get("/api/v1/goals?page=1&limit=2").session(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(third))
                .andExpect(jsonPath("$.data[1].id").value(second))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.totalItems").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
        mvc.perform(get("/api/v1/goals?page=2&limit=2").session(a))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(first));
        mvc.perform(get("/api/v1/goals?limit=101").session(a))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("createdAt is returned as UTC; timezone can be set on create and changed by PATCH")
    void createdAtAndTimezone() throws Exception {
        var s = registerAndLogin("t-" + UUID.randomUUID() + "@example.test");
        String json = body("Toán", 60, today, today.plusDays(10)).replaceFirst("\\}$", ",\"timezone\":\"America/New_York\"}");
        String res = mvc.perform(post("/api/v1/goals").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(res).get("data").get("id").asText();

        mvc.perform(patch("/api/v1/goals/" + id).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Asia/Ho_Chi_Minh\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.subject").value("Toán"));

        mvc.perform(post("/api/v1/goals").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Toán", 60, today, today.plusDays(10))
                                .replaceFirst("\\}$", ",\"timezone\":\"Mars/Olympus\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='timezone' && @.issue=='invalid_timezone')]").exists());
    }

    @Test
    @DisplayName("PATCH changes only the sent fields; PUT and missing CSRF are refused")
    void patchIsPartialAndPutIsNotAllowed() throws Exception {
        var s = registerAndLogin("r-" + UUID.randomUUID() + "@example.test");
        String id = create(s, body("Toán", 60, today, today.plusDays(10))).get("id").asText();

        mvc.perform(patch("/api/v1/goals/" + id).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutesPerDay\":200,\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minutesPerDay").value(200))
                .andExpect(jsonPath("$.data.subject").value("Toán"))
                .andExpect(jsonPath("$.data.examDate").value(today.plusDays(10).toString()));

        mvc.perform(patch("/api/v1/goals/" + id).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.issue=='empty_patch')]").exists());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/goals/" + id)
                        .session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("A", 60, today, today.plusDays(10))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/v1/goals").session(s).contentType(MediaType.APPLICATION_JSON)
                        .content(body("A", 60, today, today.plusDays(10))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
    }
}
