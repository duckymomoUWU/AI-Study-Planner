package com.studyplanner.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class AuthUserRepositoryTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AuthUserRepository repository = new AuthUserRepository(jdbc);

    private final UUID sampleId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @DisplayName("findByEmail normalizes email to lower case and queries app_users")
    void findByEmailNormalizesToLowercase() {
        var user = new AppUser(sampleId, "user@example.com", "hashed_pass", Instant.now());
        doReturn(List.of(user)).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));

        Optional<AppUser> result = repository.findByEmail("  User@Example.COM  ");

        assertTrue(result.isPresent());
        assertEquals("user@example.com", result.get().email());

        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));

        assertTrue(sql.getValue().contains("FROM app_users"));
        assertTrue(sql.getValue().contains("lower(email) = lower(:email)"));
        assertEquals("user@example.com", params.getValue().getValue("email"));
    }

    @Test
    @DisplayName("findByEmail returns empty when email is null or blank")
    void findByEmailReturnsEmptyOnBlank() {
        assertTrue(repository.findByEmail(null).isEmpty());
        assertTrue(repository.findByEmail("   ").isEmpty());
    }

    @Test
    @DisplayName("findById queries app_users by id")
    void findByIdQueriesById() {
        var user = new AppUser(sampleId, "user@example.com", "hashed_pass", Instant.now());
        doReturn(List.of(user)).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));

        Optional<AppUser> result = repository.findById(sampleId);

        assertTrue(result.isPresent());
        assertEquals(sampleId, result.get().id());

        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));

        assertTrue(sql.getValue().contains("WHERE id = :id"));
        assertEquals(sampleId, params.getValue().getValue("id"));
    }

    @Test
    @DisplayName("existsByEmail returns true when record exists")
    void existsByEmailReturnsTrue() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class)))
                .thenReturn(true);

        assertTrue(repository.existsByEmail("user@example.com"));

        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForObject(sql.capture(), params.capture(), any(Class.class));

        assertTrue(sql.getValue().contains("SELECT EXISTS"));
        assertEquals("user@example.com", params.getValue().getValue("email"));
    }

    @Test
    @DisplayName("existsByEmail returns false on blank email")
    void existsByEmailReturnsFalseOnBlank() {
        assertFalse(repository.existsByEmail(null));
        assertFalse(repository.existsByEmail(""));
    }

    @Test
    @DisplayName("insertUser normalizes email, inserts row and returns inserted AppUser")
    void insertUserSucceeds() {
        var user = new AppUser(sampleId, "user@example.com", "hashed_pass", Instant.now());
        doReturn(List.of(user)).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));

        AppUser inserted = repository.insertUser(" User@Example.COM ", "hashed_pass");

        assertEquals(sampleId, inserted.id());
        assertEquals("user@example.com", inserted.email());

        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));

        assertTrue(sql.getValue().contains("INSERT INTO app_users"));
        assertTrue(sql.getValue().contains("RETURNING id, email, password_hash, created_at"));
        assertEquals("user@example.com", params.getValue().getValue("email"));
        assertEquals("hashed_pass", params.getValue().getValue("passwordHash"));
    }

    @Test
    @DisplayName("insertUser throws NullPointerException when email or password is null")
    void insertUserRejectsNull() {
        assertThrows(NullPointerException.class, () -> repository.insertUser(null, "pass"));
        assertThrows(NullPointerException.class, () -> repository.insertUser("email@test.com", null));
    }
}
