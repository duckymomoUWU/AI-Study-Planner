package com.studyplanner.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** SQL shape checks with a mocked template; real behaviour is covered by the PostgreSQL integration test. */
class WeeklyProgressJdbcRepositoryTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final WeeklyProgressJdbcRepository repository = new WeeklyProgressJdbcRepository(jdbc);
    private final UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void oneQueryCountsTheOwnersTasksPerDayInsideTheRange() {
        repository.countByDay(owner, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        String text = sql.getValue();
        assertTrue(text.contains("FROM study_tasks t JOIN study_goals g ON g.id = t.goal_id"));
        assertTrue(text.contains("g.user_id = :ownerId"));
        assertTrue(text.contains("t.scheduled_date BETWEEN :from AND :to"));
        assertTrue(text.contains("count(*) FILTER (WHERE t.completed)"));
        assertTrue(text.contains("GROUP BY t.scheduled_date"));
        assertEquals(owner, params.getValue().getValue("ownerId"));
        assertEquals(LocalDate.of(2026, 11, 2), params.getValue().getValue("from"));
        assertEquals(LocalDate.of(2026, 11, 8), params.getValue().getValue("to"));
    }

    @Test
    void neverTouchesPlanDrafts() {
        repository.countByDay(owner, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertFalse(sql.getValue().contains("plan_drafts"));
    }

    @Test
    void refusesAnyCallWithoutAnOwnerOrARange() {
        var d = LocalDate.of(2026, 11, 2);
        assertThrows(NullPointerException.class, () -> repository.countByDay(null, d, d));
        assertThrows(NullPointerException.class, () -> repository.countByDay(owner, null, d));
        assertThrows(NullPointerException.class, () -> repository.countByDay(owner, d, null));
    }
}
