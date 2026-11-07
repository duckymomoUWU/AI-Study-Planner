package com.studyplanner.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.auth.dto.LoginRequest;
import com.studyplanner.auth.dto.RegisterRequest;

/**
 * End-to-end integration test against a disposable PostgreSQL database.
 * Run with: mvn -f backend/pom.xml test -Dauth.integration=true
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "auth.integration", matches = "true")
class AuthPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUsers() {
        jdbc.update("DELETE FROM app_users");
    }

    @Test
    @DisplayName("Complete end-to-end flow: Register -> Login -> /me -> Logout -> /me (401)")
    void completeAuthenticationFlow() throws Exception {
        String email = "student-" + UUID.randomUUID() + "@example.test";
        String password = "Password123!";

        // 1. Register new user
        var registerRequest = new RegisterRequest(email, password);
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andReturn();

        // Verify inserted into PostgreSQL with BCrypt hash
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM app_users WHERE lower(email) = lower(?)", Integer.class, email);
        assertEquals(1, count);

        // 2. Register duplicate email returns 409 Conflict
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));

        // 3. Login with wrong password returns 401 Unauthorized
        var badLogin = new LoginRequest(email, "WrongPassword!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        // 4. Login with correct password establishes session
        var goodLogin = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session, "Session should be created upon successful login");

        // 5. GET /api/v1/me with session returns authenticated user
        mockMvc.perform(get("/api/v1/me")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()));

        // 6. Logout invalidates session
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .session(session))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid(), "Session must be invalidated after logout");

        // 7. Subsequent GET /api/v1/me returns 401 Unauthorized
        mockMvc.perform(get("/api/v1/me")
                        .session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Two different accounts (User A and User B) maintain distinct isolated sessions")
    void twoAccountsAreIsolated() throws Exception {
        String emailA = "user-a-" + UUID.randomUUID() + "@example.test";
        String emailB = "user-b-" + UUID.randomUUID() + "@example.test";
        String password = "Password123!";

        // Register A and B
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(emailA, password))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(emailB, password))))
                .andExpect(status().isCreated());

        // Login A
        MvcResult loginA = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(emailA, password))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession sessionA = (MockHttpSession) loginA.getRequest().getSession(false);

        // Login B
        MvcResult loginB = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(emailB, password))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession sessionB = (MockHttpSession) loginB.getRequest().getSession(false);

        // Check A
        mockMvc.perform(get("/api/v1/me").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(emailA.toLowerCase()));

        // Check B
        mockMvc.perform(get("/api/v1/me").session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(emailB.toLowerCase()));
    }
}
