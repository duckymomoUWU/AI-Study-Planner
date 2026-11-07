package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(creds))
                .andExpect(status().isCreated());
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(creds))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String body(String subject, int minutes, LocalDate start, LocalDate exam) {
        return "{\"subject\":\"" + subject + "\",\"topics\":\"Tích phân\",\"startDate\":\"" + start
                + "\",\"examDate\":\"" + exam + "\",\"minutesPerDay\":" + minutes + "}";
    }

    private JsonNode create(MockHttpSession s, String json) throws Exception {
        String res = mvc.perform(post("/api/goals").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res);
    }

    @Test
    @DisplayName("create then list and read back from the database; trimmed text; owner from session")
    void createListAndReadPersist() throws Exception {
        var s = registerAndLogin("a-" + UUID.randomUUID() + "@example.test");
        JsonNode created = create(s, body("  Giải tích  ", 90, today, today.plusDays(30)));
        assertEquals("Giải tích", created.get("subject").asText());
        assertEquals(0, created.get("version").asInt());

        mvc.perform(get("/api/goals").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(created.get("id").asText()));
        mvc.perform(get("/api/goals/" + created.get("id").asText()).session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Giải tích"))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM study_goals", Integer.class);
        assertEquals(1, rows);
    }

    @Test
    @DisplayName("client-supplied userId is ignored; goal belongs to the logged-in user")
    void ownerComesFromSession() throws Exception {
        var s = registerAndLogin("b-" + UUID.randomUUID() + "@example.test");
        UUID other = UUID.randomUUID();
        String json = body("Toán", 60, today, today.plusDays(10)).replaceFirst("\\{", "{\"userId\":\"" + other + "\",");
        create(s, json);
        Integer foreign = jdbc.queryForObject("SELECT count(*) FROM study_goals WHERE user_id = ?",
                Integer.class, other);
        assertEquals(0, foreign);
    }

    @Test
    @DisplayName("another account cannot read a goal by ID or see it in the list")
    void crossAccountAccessIsRejected() throws Exception {
        var a = registerAndLogin("c-" + UUID.randomUUID() + "@example.test");
        var b = registerAndLogin("d-" + UUID.randomUUID() + "@example.test");
        JsonNode goal = create(a, body("Toán", 60, today, today.plusDays(10)));

        mvc.perform(get("/api/goals/" + goal.get("id").asText()).session(b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("goal_not_found"));
        mvc.perform(get("/api/goals").session(b))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/goals/" + goal.get("id").asText()))
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
        assertRejected(s, body("Toán", 60, today, today.plusDays(91)), "examDate", "over_90_days");
        assertRejected(s, body("Toán", 60, today, today), "examDate", "not_after_start");
        assertRejected(s, body("Toán", 60, today.minusDays(20), today.minusDays(1)), "examDate", "past_date");
        assertRejected(s, body("   ", 60, today, today.plusDays(10)), "subject", "required");
        assertRejected(s, body("Toán", 60, today, today.plusDays(10)).replace("Tích phân", "  "),
                "topics", "required");

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM study_goals", Integer.class);
        assertEquals(2, rows);
    }

    private void assertRejected(MockHttpSession s, String json, String field, String code) throws Exception {
        mvc.perform(post("/api/goals").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='" + field + "' && @.code=='" + code + "')]").exists());
    }
}
