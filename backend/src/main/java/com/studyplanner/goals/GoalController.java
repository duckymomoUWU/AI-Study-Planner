package com.studyplanner.goals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.auth.AppUserPrincipal;

/**
 * ASP-23: create, list and read goals. The owner always comes from the authenticated
 * principal (ASP-12), never from the request. Update and delete belong to later tasks.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {
    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    public record GoalListResponse(List<GoalResponse> items) {}

    @GetMapping
    public GoalListResponse list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return new GoalListResponse(service.listGoals(owner(principal)).stream()
                .map(GoalResponse::from).toList());
    }

    @GetMapping("/{goalId}")
    public GoalResponse get(@AuthenticationPrincipal AppUserPrincipal principal,
                            @PathVariable UUID goalId) {
        return GoalResponse.from(service.getGoal(owner(principal), goalId));
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                               @RequestBody GoalRequest request) {
        UUID owner = owner(principal);
        GoalRequest.Parsed parsed = request.toInput();
        rejectParseErrors(parsed);
        var created = service.createGoal(owner, parsed.input());
        return ResponseEntity.status(HttpStatus.CREATED).body(GoalResponse.from(created));
    }

    private static UUID owner(AppUserPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        return principal.id();
    }

    /**
     * When parsing found type or format errors, report them together with the remaining
     * required-field errors so the UI can mark every bad field at once.
     */
    private static void rejectParseErrors(GoalRequest.Parsed parsed) {
        if (parsed.errors().isEmpty()) {
            return;
        }
        var all = new ArrayList<>(parsed.errors());
        var seen = all.stream().map(GoalPolicy.Violation::field).toList();
        GoalPolicy.validate(parsed.input(), LocalDate.MIN).stream()
                .filter(v -> !seen.contains(v.field()))
                .forEach(all::add);
        throw new GoalService.GoalValidationException(all);
    }

    static final class UnauthenticatedException extends RuntimeException {}
}
