package com.studyplanner.goals;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.api.*;
import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.auth.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/goals")
public class PlanDraftController {
    private final PlanDraftService service;
    public PlanDraftController(PlanDraftService service) { this.service = service; }

    @PostMapping("/{goalId}/plan-drafts")
    public ResponseEntity<ApiResponse<PlanDraftService.DraftResponse>> generate(
            @AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID goalId,
            @RequestBody(required = false) JsonNode body) {
        UUID user = owner(principal);
        if (body != null && (!body.isObject() || !body.isEmpty()))
            throw new RequestValidationException(List.of(new ErrorDetail("body", "unknown_field")));
        return ResponseEntity.status(201).body(ApiResponse.of(service.generate(user, goalId)));
    }

    @GetMapping("/{goalId}/plan-drafts/{draftId}")
    public ApiResponse<PlanDraftService.DraftResponse> get(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId, @PathVariable UUID draftId) {
        return ApiResponse.of(service.get(owner(principal), goalId, draftId));
    }

    @GetMapping("/{goalId}/plan-drafts/latest")
    public ApiResponse<PlanDraftService.DraftResponse> latest(@AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID goalId) { return ApiResponse.of(service.latest(owner(principal), goalId)); }

    @PostMapping("/{goalId}/plan-drafts/{draftId}/confirm")
    public ResponseEntity<ApiResponse<PlanDraftService.ConfirmResponse>> confirm(
            @AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID goalId,
            @PathVariable UUID draftId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode body) {
        UUID user = owner(principal);
        UUID parsed;
        try {
            parsed = UUID.fromString(key);
            if (!parsed.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new RequestValidationException(List.of(new ErrorDetail("Idempotency-Key", "invalid_type")));
        }
        var result = service.confirm(user, goalId, draftId, parsed, body);
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(ApiResponse.of(result.result()));
    }

    private static UUID owner(AppUserPrincipal principal) {
        if (principal == null) throw new UnauthenticatedException();
        return principal.id();
    }
}
