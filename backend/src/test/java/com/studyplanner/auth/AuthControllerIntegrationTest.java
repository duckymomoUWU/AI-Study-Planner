package com.studyplanner.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.auth.dto.LoginRequest;
import com.studyplanner.auth.dto.RegisterRequest;
import com.studyplanner.auth.dto.UserResponse;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private AuthUserRepository authUserRepository;

    private final UUID sampleId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // ==========================================
    // CSRF Endpoint Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/auth/csrf returns token details and sets XSRF-TOKEN cookie")
    void getCsrfReturnsTokenAndSetsCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    // ==========================================
    // Registration Tests
    // ==========================================

    @Test
    @DisplayName("POST /api/auth/register returns 201 Created on valid input with CSRF")
    void registerSucceeds() throws Exception {
        var request = new RegisterRequest("student@example.com", "Password123!");
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(sampleId, "student@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sampleId.toString()))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register rejects request with 403 Forbidden when CSRF is missing")
    void registerRejectsMissingCsrf() throws Exception {
        var request = new RegisterRequest("student@example.com", "Password123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden_or_csrf"));
    }

    @Test
    @DisplayName("POST /api/auth/register rejects request with 403 Forbidden when CSRF is invalid")
    void registerRejectsInvalidCsrf() throws Exception {
        var request = new RegisterRequest("student@example.com", "Password123!");

        mockMvc.perform(post("/api/auth/register")
                        .header("X-CSRF-TOKEN", "invalid-token-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden_or_csrf"));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 Conflict when email is already registered")
    void registerRejectsDuplicateEmail() throws Exception {
        var request = new RegisterRequest("duplicate@example.com", "Password123!");
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateEmailException("Email đã được sử dụng."));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_exists"))
                .andExpect(jsonPath("$.message").value("Email đã được sử dụng."));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 Bad Request on invalid email format")
    void registerRejectsInvalidEmailFormat() throws Exception {
        var request = new RegisterRequest("not-an-email", "Password123!");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 Bad Request on password shorter than 6 characters")
    void registerRejectsShortPassword() throws Exception {
        var request = new RegisterRequest("student@example.com", "12345");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    // ==========================================
    // Login Tests
    // ==========================================

    @Test
    @DisplayName("POST /api/auth/login returns 200 OK and establishes session on valid credentials")
    void loginSucceeds() throws Exception {
        var request = new LoginRequest("student@example.com", "Password123!");
        var principal = new AppUserPrincipal(sampleId, "student@example.com", "hashed_pass");
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authService.authenticate(eq("student@example.com"), eq("Password123!")))
                .thenReturn(auth);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleId.toString()))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(header().exists("X-CSRF-TOKEN"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 Unauthorized on invalid credentials")
    void loginRejectsWrongPassword() throws Exception {
        var request = new LoginRequest("student@example.com", "WrongPassword");
        when(authService.authenticate(eq("student@example.com"), eq("WrongPassword")))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"))
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không chính xác."));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 Bad Request on blank email or password")
    void loginRejectsBlankFields() throws Exception {
        var request = new LoginRequest("", "");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 403 Forbidden without CSRF token")
    void loginRejectsMissingCsrf() throws Exception {
        var request = new LoginRequest("student@example.com", "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden_or_csrf"));
    }

    // ==========================================
    // GET /api/auth/me Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/auth/me returns 200 OK with authenticated user from session")
    void meReturnsCurrentUser() throws Exception {
        var principal = new AppUserPrincipal(sampleId, "student@example.com", "hashed_pass");

        mockMvc.perform(get("/api/auth/me")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleId.toString()))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 Unauthorized when unauthenticated")
    void meRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"))
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập."));
    }

    @Test
    @DisplayName("GET /api/auth/me ignores spoofed userId parameter and uses session principal")
    void meIgnoresSpoofedUserIdParameter() throws Exception {
        var principal = new AppUserPrincipal(sampleId, "student@example.com", "hashed_pass");
        UUID spoofedId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        mockMvc.perform(get("/api/auth/me?userId=" + spoofedId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleId.toString())) // Still returns sampleId from session
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(spoofedId.toString())));
    }

    // ==========================================
    // Logout Tests
    // ==========================================

    @Test
    @DisplayName("POST /api/auth/logout returns 204 No Content with CSRF")
    void logoutSucceedsWithCsrf() throws Exception {
        var principal = new AppUserPrincipal(sampleId, "student@example.com", "hashed_pass");

        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .with(user(principal)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/auth/logout returns 403 Forbidden without CSRF token")
    void logoutRejectsMissingCsrf() throws Exception {
        var principal = new AppUserPrincipal(sampleId, "student@example.com", "hashed_pass");

        mockMvc.perform(post("/api/auth/logout")
                        .with(user(principal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden_or_csrf"));
    }

    // ==========================================
    // General Protected Endpoint Boundary Tests
    // ==========================================

    @Test
    @DisplayName("Access to general /api/* endpoint returns 401 Unauthorized when unauthenticated")
    void protectedApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"))
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập."));
    }
}
