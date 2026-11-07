package com.studyplanner.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** SQL shape checks with a mocked template; real behaviour is covered by the PostgreSQL integration test. */
class ProgressJdbcRepositoryTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final ProgressJdbcRepository repository = new ProgressJdbcRepository(jdbc);
    private final UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID goal = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void oneQueryCountsTasksOfTheOwnedGoalOnly() {
        repository.countOwned(owner, goal);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        String text = sql.getValue();
        assertTrue(text.contains("FROM study_goals g LEFT JOIN study_tasks t ON t.goal_id = g.id"));
        assertTrue(text.contains("g.user_id = :ownerId AND g.id = :goalId"));
        assertTrue(text.contains("count(t.id) FILTER (WHERE t.completed)"));
        assertTrue(text.contains("GROUP BY g.id"));
        assertEquals(owner, params.getValue().getValue("ownerId"));
        assertEquals(goal, params.getValue().getValue("goalId"));
    }

    @Test
    void neverTouchesPlanDrafts() {
        repository.countOwned(owner, goal);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(!sql.getValue().contains("plan_drafts"));
    }

    @Test
    void refusesAnyCallWithoutATrustedOwnerOrGoal() {
        assertThrows(NullPointerException.class, () -> repository.countOwned(null, goal));
        assertThrows(NullPointerException.class, () -> repository.countOwned(owner, null));
    }
}
