package com.studyplanner.goals;

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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class GoalJdbcRepositoryTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final GoalJdbcRepository repository = new GoalJdbcRepository(jdbc);
    private final UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID goalId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private GoalPolicy.GoalInput input() {
        return new GoalPolicy.GoalInput("Toán", "Giải tích",
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 5), 60);
    }

    @Test
    void listScopesQueryToOwner() {
        repository.listOwned(ownerId);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("WHERE user_id = :ownerId"));
        assertEquals(ownerId, params.getValue().getValue("ownerId"));
    }

    @Test
    void lockReadsOnlyOwnedGoalAndLocksItForUpdate() {
        repository.lockOwned(ownerId, goalId);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("user_id = :ownerId AND id = :goalId FOR UPDATE"));
        assertEquals(ownerId, params.getValue().getValue("ownerId"));
        assertEquals(goalId, params.getValue().getValue("goalId"));
    }

    @Test
    void updateUsesOwnerAndOptimisticVersionCheck() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        assertTrue(repository.updateOwnedIfVersion(ownerId, goalId, input(), 4));

        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertTrue(sql.getValue().contains("user_id = :ownerId"));
        assertTrue(sql.getValue().contains("version = :expectedVersion"));
        assertTrue(sql.getValue().contains("version = version + 1"));
        assertEquals(4, params.getValue().getValue("expectedVersion"));
        assertEquals(goalId, params.getValue().getValue("goalId"));
        assertEquals(ownerId, params.getValue().getValue("ownerId"));
    }

    @Test
    void updateRejectsNegativeVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.updateOwnedIfVersion(ownerId, goalId, input(), -1));
    }

    @Test
    void deleteRequiresOwnerAndOnlyDeletesMatchingGoal() {
        assertFalse(repository.deleteOwned(ownerId, goalId));

        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertTrue(sql.getValue().contains("DELETE FROM study_goals WHERE user_id = :ownerId AND id = :goalId"));
        assertEquals(ownerId, params.getValue().getValue("ownerId"));
        assertEquals(goalId, params.getValue().getValue("goalId"));
    }

    @Test
    void taskReadChecksGoalOwnershipViaJoin() {
        repository.listScheduledTasksOwned(ownerId, goalId);
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("JOIN study_goals g ON g.id = t.goal_id"));
        assertTrue(sql.getValue().contains("g.user_id = :ownerId AND g.id = :goalId"));
        assertEquals(ownerId, params.getValue().getValue("ownerId"));
    }

    @Test
    void refusesAnyCallWithoutTrustedOwner() {
        assertThrows(NullPointerException.class, () -> repository.listOwned(null));
        assertThrows(NullPointerException.class, () -> repository.insertOwned(null, input(), "Asia/Ho_Chi_Minh"));
        assertThrows(NullPointerException.class, () -> repository.deleteOwned(null, goalId));
    }

    @Test
    void insertObtainsGoalFromReturningWithTrustedOwner() {
        var stored = new GoalJdbcRepository.StoredGoal(goalId, ownerId, "Toán", "Giải tích",
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 5), 60, "Asia/Ho_Chi_Minh", 0);
        doReturn(List.of(stored)).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        assertEquals(stored, repository.insertOwned(ownerId, input(), "Asia/Ho_Chi_Minh"));

        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
        assertTrue(sql.getValue().contains("RETURNING"));
        assertEquals(ownerId, params.getValue().getValue("ownerId"));
        assertEquals("Asia/Ho_Chi_Minh", params.getValue().getValue("timezone"));
    }
}
