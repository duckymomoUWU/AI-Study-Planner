package com.studyplanner.goals;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.auth.AppUserPrincipal;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = Objects.requireNonNull(goalService, "goalService must not be null");
    }

    public record GoalResponse(UUID id, String subject, String topics, LocalDate startDate,
                              LocalDate examDate, int minutesPerDay, String timezone, int version) {
        public static GoalResponse from(GoalJdbcRepository.StoredGoal stored) {
            return new GoalResponse(
                    stored.id(),
                    stored.subject(),
                    stored.topics(),
                    stored.startDate(),
                    stored.examDate(),
                    stored.minutesPerDay(),
                    stored.timezone(),
                    stored.version()
            );
        }
    }

    public record CreateGoalRequest(String subject, String topics, LocalDate startDate,
                                   LocalDate examDate, Integer minutesPerDay) {}

    public record UpdateGoalRequest(String subject, String topics, LocalDate startDate,
                                   LocalDate examDate, Integer minutesPerDay, Integer version) {}

    @GetMapping
    public ResponseEntity<?> listGoals(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<GoalResponse> list = goalService.listGoals(principal.id()).stream()
                .map(GoalResponse::from)
                .toList();
        return ResponseEntity.ok(Map.of("items", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @PathVariable("id") UUID id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var goal = goalService.getGoal(principal.id(), id);
        return ResponseEntity.ok(GoalResponse.from(goal));
    }

    @PostMapping
    public ResponseEntity<?> createGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @RequestBody CreateGoalRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GoalPolicy.GoalInput input = new GoalPolicy.GoalInput(
                request.subject(),
                request.topics(),
                request.startDate(),
                request.examDate(),
                request.minutesPerDay()
        );
        var created = goalService.createGoal(principal.id(), input);
        return ResponseEntity.status(HttpStatus.CREATED).body(GoalResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable("id") UUID id,
                                       @RequestBody UpdateGoalRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GoalPolicy.GoalInput input = new GoalPolicy.GoalInput(
                request.subject(),
                request.topics(),
                request.startDate(),
                request.examDate(),
                request.minutesPerDay()
        );
        int version = request.version() == null ? 0 : request.version();
        var updated = goalService.updateGoal(principal.id(), id, input, version);
        return ResponseEntity.ok(GoalResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable("id") UUID id) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        goalService.deleteGoal(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(GoalService.GoalValidationException.class)
    public ResponseEntity<?> handleValidation(GoalService.GoalValidationException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "code", "validation_failed",
                "message", "Dữ liệu mục tiêu không hợp lệ.",
                "fieldErrors", e.violations()
        ));
    }

    @ExceptionHandler(GoalService.GoalNotFoundException.class)
    public ResponseEntity<?> handleNotFound(GoalService.GoalNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "goal_not_found",
                "message", "Mục tiêu không tồn tại hoặc đã bị xóa."
        ));
    }

    @ExceptionHandler(GoalService.GoalConflictException.class)
    public ResponseEntity<?> handleConflict(GoalService.GoalConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "stale_goal_version",
                "message", "Mục tiêu vừa được cập nhật ở nơi khác. Vui lòng tải lại trước khi tiếp tục."
        ));
    }
}
