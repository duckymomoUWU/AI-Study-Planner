package com.studyplanner.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** SQL shape checks with a mocked template; real behaviour is covered by the PostgreSQL integration test. */
class TaskJdbcRepositoryTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final TaskJdbcRepository repository = new TaskJdbcRepository(jdbc);
    private final UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID goal = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID task = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void findJoinsTheGoalAndFiltersOwnerGoalAndTask() {
        repository.findOwned(owner, goal, task);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("JOIN study_goals g ON g.id = t.goal_id"));
        assertTrue(sql.getValue().contains("g.user_id = :ownerId AND g.id = :goalId AND t.id = :taskId"));
        assertEquals(owner, params.getValue().getValue("ownerId"));
        assertEquals(goal, params.getValue().getValue("goalId"));
        assertEquals(task, params.getValue().getValue("taskId"));
    }

    @Test
    void updateCompletedIsScopedToOwnerGoalAndTask() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        assertTrue(repository.updateCompleted(owner, goal, task, true));
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertTrue(sql.getValue().contains("g.user_id = :ownerId AND g.id = :goalId AND t.id = :taskId"));
        assertEquals(true, params.getValue().getValue("completed"));

        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
        assertFalse(repository.updateCompleted(owner, goal, task, false));
    }

    @Test
    void dayListIsOwnerScopedFilteredByDateOrderedAndPaged() {
        repository.listByDate(owner, LocalDate.of(2026, 11, 10), 20, 40);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("g.user_id = :ownerId AND t.scheduled_date = :date"));
        assertTrue(sql.getValue().contains("ORDER BY t.scheduled_date ASC, t.created_at ASC, t.id ASC"));
        assertTrue(sql.getValue().contains("LIMIT :limit OFFSET :offset"));
        assertEquals(40L, params.getValue().getValue("offset"));
    }

    @Test
    void goalListAddsOnlyTheFiltersThatWereGiven() {
        repository.listByGoal(owner, goal, null, null, 20, 0);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(sql.getValue().contains("g.user_id = :ownerId AND g.id = :goalId"));
        assertFalse(sql.getValue().contains(":date"));
        assertFalse(sql.getValue().contains("t.completed = :completed"));

        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(3L);
        assertEquals(3L, repository.countByGoal(owner, goal, LocalDate.of(2026, 11, 10), false));
        var countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(countSql.capture(), any(SqlParameterSource.class), eq(Long.class));
        assertTrue(countSql.getValue().contains("t.scheduled_date = :date"));
        assertTrue(countSql.getValue().contains("t.completed = :completed"));
    }

    @Test
    void refusesAnyCallWithoutATrustedOwner() {
        assertThrows(NullPointerException.class, () -> repository.findOwned(null, goal, task));
        assertThrows(NullPointerException.class, () -> repository.updateCompleted(null, goal, task, true));
        assertThrows(NullPointerException.class, () -> repository.listByDate(null, LocalDate.now(), 20, 0));
        assertThrows(NullPointerException.class, () -> repository.listByGoal(null, goal, null, null, 20, 0));
    }
}
