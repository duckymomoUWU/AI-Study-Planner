package com.studyplanner.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Owner-scoped repository for user accounts using PostgreSQL.
 */
@Repository
public class AuthUserRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AuthUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    public Optional<AppUser> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String sql = """
            SELECT id, email, password_hash, created_at
            FROM app_users
            WHERE lower(email) = lower(:email)
            """;
        var params = new MapSqlParameterSource("email", email.trim().toLowerCase(Locale.ROOT));
        List<AppUser> results = jdbc.query(sql, params, new AppUserRowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<AppUser> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT id, email, password_hash, created_at
            FROM app_users
            WHERE id = :id
            """;
        var params = new MapSqlParameterSource("id", id);
        List<AppUser> results = jdbc.query(sql, params, new AppUserRowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String sql = "SELECT EXISTS(SELECT 1 FROM app_users WHERE lower(email) = lower(:email))";
        var params = new MapSqlParameterSource("email", email.trim().toLowerCase(Locale.ROOT));
        Boolean exists = jdbc.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public AppUser insertUser(String email, String passwordHash) {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String sql = """
            INSERT INTO app_users (email, password_hash)
            VALUES (:email, :passwordHash)
            RETURNING id, email, password_hash, created_at
            """;
        var params = new MapSqlParameterSource()
                .addValue("email", normalizedEmail)
                .addValue("passwordHash", passwordHash);
        List<AppUser> results = jdbc.query(sql, params, new AppUserRowMapper());
        if (results.isEmpty()) {
            throw new IllegalStateException("Failed to insert user into database");
        }
        return results.get(0);
    }

    private static class AppUserRowMapper implements RowMapper<AppUser> {
        @Override
        public AppUser mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID id = rs.getObject("id", UUID.class);
            String email = rs.getString("email");
            String passwordHash = rs.getString("password_hash");
            Timestamp createdAt = rs.getTimestamp("created_at");
            Instant instant = createdAt != null ? createdAt.toInstant() : Instant.now();
            return new AppUser(id, email, passwordHash, instant);
        }
    }
}
