package com.studyplanner.goals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.api.ApiResponse;
import com.studyplanner.api.Paging;
import com.studyplanner.api.UnauthenticatedException;
import com.studyplanner.auth.AppUserPrincipal;

/**
 * Goal endpoints under /api/v1/goals (docs/backend-api-rules.md). HTTP only: parse, call the service, wrap in
 * the envelope. The owner always comes from the authenticated principal, never from the request.
 */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {
    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<GoalResponse>> list(@AuthenticationPrincipal AppUserPrincipal principal,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer limit) {
        UUID owner = owner(principal);
        Paging paging = Paging.of(page, limit);
        GoalService.GoalPage result = service.listGoals(owner, paging.page(), paging.limit());
        return ApiResponse.page(result.items().stream().map(GoalResponse::from).toList(),
                paging.page(), paging.limit(), result.totalItems());
    }

    @GetMapping("/{goalId}")
    public ApiResponse<GoalResponse> get(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @PathVariable UUID goalId) {
        return ApiResponse.of(GoalResponse.from(service.getGoal(owner(principal), goalId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                            @RequestBody JsonNode body) {
        UUID owner = owner(principal);
        GoalRequest.Create parsed = GoalRequest.parseCreate(body);
        rejectParseErrors(parsed.errors(), parsed.input());
        var created = service.createGoal(owner, parsed.input(), parsed.timezone());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(GoalResponse.from(created)));
    }

    @PatchMapping("/{goalId}")
    public ApiResponse<GoalResponse> patch(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @PathVariable UUID goalId, @RequestBody JsonNode body) {
        UUID owner = owner(principal);
        GoalRequest.Patch parsed = GoalRequest.parsePatch(body);
        if (!parsed.errors().isEmpty()) {
            throw new GoalService.GoalValidationException(parsed.errors());
        }
        return ApiResponse.of(GoalResponse.from(
                service.patchGoal(owner, goalId, parsed.patch(), parsed.expectedVersion())));
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable UUID goalId) {
        service.deleteGoal(owner(principal), goalId);
        return ResponseEntity.noContent().build();
    }

    private static UUID owner(AppUserPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        return principal.id();
    }

    /**
     * If parsing already found problems, report them together with the remaining required-field errors
     * so the UI can mark every bad field at once.
     */
    private static void rejectParseErrors(List<GoalPolicy.Violation> parseErrors, GoalPolicy.GoalInput input) {
        if (parseErrors.isEmpty()) {
            return;
        }
        var all = new ArrayList<>(parseErrors);
        var seen = all.stream().map(GoalPolicy.Violation::field).toList();
        GoalPolicy.validate(input, LocalDate.MIN).stream()
                .filter(v -> !seen.contains(v.field()))
                .forEach(all::add);
        throw new GoalService.GoalValidationException(all);
    }
}
