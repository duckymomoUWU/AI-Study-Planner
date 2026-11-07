package com.studyplanner.goals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.auth.AppUser;
import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.auth.SecurityConfig;
import com.studyplanner.goals.dto.CreateGoalRequest;
import com.studyplanner.goals.dto.UpdateGoalRequest;

@WebMvcTest(controllers = GoalController.class)
@Import({SecurityConfig.class, GoalExceptionHandler.class})
class GoalControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GoalService goalService;

    @MockBean
    private UserDetailsService userDetailsService;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID goalId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private AppUserPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new AppUserPrincipal(userId, "student@example.com", "secret");
    }

    private GoalJdbcRepository.StoredGoal sampleGoal(int version) {
        return new GoalJdbcRepository.StoredGoal(
                goalId, userId, "Giải tích 2", "Tích phân đường",
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 5),
                90, "Asia/Ho_Chi_Minh", version
        );
    }

    @Test
    @DisplayName("GET /api/goals lists user goals with items array for frontend compatibility")
    void listGoalsSucceeds() throws Exception {
        when(goalService.listGoals(userId)).thenReturn(List.of(sampleGoal(0)));

        mockMvc.perform(get("/api/goals")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(goalId.toString()))
                .andExpect(jsonPath("$.items[0].subject").value("Giải tích 2"))
                .andExpect(jsonPath("$.data[0].id").value(goalId.toString()));
    }

    @Test
    @DisplayName("POST /api/goals creates goal and returns 201")
    void createGoalSucceeds() throws Exception {
        var req = new CreateGoalRequest("Giải tích 2", "Tích phân đường",
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 5), 90, null);

        when(goalService.createGoal(eq(userId), any(GoalPolicy.GoalInput.class)))
                .thenReturn(sampleGoal(0));

        mockMvc.perform(post("/api/goals")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(goalId.toString()))
                .andExpect(jsonPath("$.subject").value("Giải tích 2"))
                .andExpect(jsonPath("$.minutesPerDay").value(90));
    }

    @Test
    @DisplayName("PUT /api/goals/{goalId} updates goal and increments version")
    void updateGoalSucceeds() throws Exception {
        var req = new UpdateGoalRequest("Giải tích 2", "Tích phân đường",
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 5), 90, 0, null);

        when(goalService.getGoal(userId, goalId)).thenReturn(sampleGoal(0));
        when(goalService.updateGoal(eq(userId), eq(goalId), any(GoalPolicy.GoalInput.class), eq(0)))
                .thenReturn(sampleGoal(1));

        mockMvc.perform(put("/api/goals/{goalId}", goalId)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("DELETE /api/goals/{goalId} deletes goal and returns 204")
    void deleteGoalSucceeds() throws Exception {
        doNothing().when(goalService).deleteGoal(userId, goalId);

        mockMvc.perform(delete("/api/goals/{goalId}", goalId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
