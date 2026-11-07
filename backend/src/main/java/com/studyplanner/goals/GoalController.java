package com.studyplanner.goals;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.goals.dto.CreateGoalRequest;
import com.studyplanner.goals.dto.GoalListResponse;
import com.studyplanner.goals.dto.GoalResponse;
import com.studyplanner.goals.dto.UpdateGoalRequest;
import com.studyplanner.tasks.dto.PaginationMeta;
import com.studyplanner.tasks.dto.TaskErrorResponse;

import jakarta.validation.Valid;

@RestController
public class GoalController {
    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = Objects.requireNonNull(goalService, "goalService");
    }

    private static GoalResponse toResponse(GoalJdbcRepository.StoredGoal g) {
        return GoalResponse.of(
                g.id(),
                g.subject(),
                g.topics(),
                g.startDate(),
                g.examDate(),
                g.minutesPerDay(),
                g.timezone(),
                g.version()
        );
    }

    @PostMapping({"/api/goals", "/api/v1/goals"})
    public ResponseEntity<?> createGoal(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateGoalRequest request) {
        if (principal == null) {
            return unauthenticated();
        }

        GoalPolicy.GoalInput input = new GoalPolicy.GoalInput(
                request.subject(),
                request.topics(),
                request.startDate(),
                request.examDate(),
                request.minutesPerDay()
        );

        GoalJdbcRepository.StoredGoal created = goalService.createGoal(principal.id(), input);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping({"/api/goals", "/api/v1/goals"})
    public ResponseEntity<?> listGoals(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        if (principal == null) {
            return unauthenticated();
        }

        List<GoalJdbcRepository.StoredGoal> storedList = goalService.listGoals(principal.id());
        List<GoalResponse> responses = storedList.stream().map(GoalController::toResponse).toList();

        return ResponseEntity.ok(GoalListResponse.of(
                responses,
                PaginationMeta.of(page, limit, responses.size())
        ));
    }

    @GetMapping({"/api/goals/{goalId}", "/api/v1/goals/{goalId}"})
    public ResponseEntity<?> getGoal(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId) {
        if (principal == null) {
            return unauthenticated();
        }

        GoalJdbcRepository.StoredGoal stored = goalService.getGoal(principal.id(), goalId);
        return ResponseEntity.ok(toResponse(stored));
    }

    @PutMapping({"/api/goals/{goalId}", "/api/v1/goals/{goalId}"})
    public ResponseEntity<?> putGoal(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId,
            @Valid @RequestBody UpdateGoalRequest request) {
        return handleUpdate(principal, goalId, request);
    }

    @PatchMapping({"/api/goals/{goalId}", "/api/v1/goals/{goalId}"})
    public ResponseEntity<?> patchGoal(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId,
            @Valid @RequestBody UpdateGoalRequest request) {
        return handleUpdate(principal, goalId, request);
    }

    private ResponseEntity<?> handleUpdate(AppUserPrincipal principal, UUID goalId, UpdateGoalRequest request) {
        if (principal == null) {
            return unauthenticated();
        }

        GoalJdbcRepository.StoredGoal current = goalService.getGoal(principal.id(), goalId);
        int expectedVersion = request.resolveExpectedVersion(current.version());

        String subject = request.subject() != null ? request.subject() : current.subject();
        String topics = request.topics() != null ? request.topics() : current.topics();
        var startDate = request.startDate() != null ? request.startDate() : current.startDate();
        var examDate = request.examDate() != null ? request.examDate() : current.examDate();
        int minutesPerDay = request.minutesPerDay() != null ? request.minutesPerDay() : current.minutesPerDay();

        GoalPolicy.GoalInput input = new GoalPolicy.GoalInput(subject, topics, startDate, examDate, minutesPerDay);
        GoalJdbcRepository.StoredGoal updated = goalService.updateGoal(principal.id(), goalId, input, expectedVersion);

        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping({"/api/goals/{goalId}", "/api/v1/goals/{goalId}"})
    public ResponseEntity<?> deleteGoal(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId) {
        if (principal == null) {
            return unauthenticated();
        }

        goalService.deleteGoal(principal.id(), goalId);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<TaskErrorResponse> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(TaskErrorResponse.of("unauthenticated", "Báº¡n cáº§n Ä‘Äƒng nháº­p."));
    }
}
