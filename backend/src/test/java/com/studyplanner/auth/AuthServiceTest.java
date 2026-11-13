package com.studyplanner.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.studyplanner.auth.dto.RegisterRequest;
import com.studyplanner.auth.dto.UserResponse;

class AuthServiceTest {
    private AuthUserRepository repository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private AuthService service;

    private final UUID sampleId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        repository = mock(AuthUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        service = new AuthService(repository, passwordEncoder, authenticationManager);
    }

    @Test
    @DisplayName("register hashes password, saves user and returns response without password hash")
    void registerSucceeds() {
        var request = new RegisterRequest("Student@Example.COM", "Secret123!");
        when(repository.existsByEmail("student@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("$2a$12$hashedSecret");
        when(repository.insertUser("student@example.com", "$2a$12$hashedSecret"))
                .thenReturn(new AppUser(sampleId, "student@example.com", "$2a$12$hashedSecret", Instant.now()));

        UserResponse response = service.register(request);

        assertNotNull(response);
        assertEquals(sampleId, response.id());
        assertEquals("student@example.com", response.email());
        assertNotNull(response.createdAt());
        verify(repository).insertUser("student@example.com", "$2a$12$hashedSecret");
    }

    @Test
    @DisplayName("register throws DuplicateEmailException when email already exists")
    void registerRejectsDuplicateEmail() {
        var request = new RegisterRequest("existing@example.com", "Secret123!");
        when(repository.existsByEmail("existing@example.com")).thenReturn(true);

        var ex = assertThrows(DuplicateEmailException.class, () -> service.register(request));
        assertEquals("Email đã được sử dụng.", ex.getMessage());
    }

    @Test
    @DisplayName("authenticate delegates to AuthenticationManager with normalized email")
    void authenticateDelegates() {
        var expectedAuth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(expectedAuth);

        Authentication auth = service.authenticate(" User@Test.com ", "pass123");

        assertEquals(expectedAuth, auth);
        verify(authenticationManager).authenticate(
                eq(new UsernamePasswordAuthenticationToken("user@test.com", "pass123"))
        );
    }

    @Test
    @DisplayName("authenticate throws BadCredentialsException on invalid credentials")
    void authenticateThrowsOnBadCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> service.authenticate("user@test.com", "wrong"));
    }

    @Test
    @DisplayName("getUser returns the public account view including createdAt")
    void getUserReturnsUserResponse() {
        Instant created = Instant.parse("2026-11-07T03:00:00Z");
        when(repository.findById(sampleId))
                .thenReturn(Optional.of(new AppUser(sampleId, "user@test.com", "hash", created)));

        UserResponse user = service.getUser(sampleId).orElseThrow();

        assertEquals(sampleId, user.id());
        assertEquals("user@test.com", user.email());
        assertEquals(created, user.createdAt());
    }

    @Test
    @DisplayName("getUser is empty when the account no longer exists")
    void getUserIsEmptyForUnknownAccount() {
        when(repository.findById(sampleId)).thenReturn(Optional.empty());
        assertTrue(service.getUser(sampleId).isEmpty());
    }
}
