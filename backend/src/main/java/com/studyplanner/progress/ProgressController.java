package com.studyplanner.progress;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.api.ApiResponse;
import com.studyplanner.api.UnauthenticatedException;
import com.studyplanner.auth.AppUserPrincipal;

/**
 * GET /api/v1/goals/{goalId}/progress (docs/backend-api-rules.md). Read only; the owner comes from the principal.
 * An unknown goal and another user's goal are the same 404.
 */
@RestController
@RequestMapping("/api/v1/goals/{goalId}/progress")
public class ProgressController {
    private final ProgressService service;

    public ProgressController(ProgressService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ProgressService.Progress> get(@AuthenticationPrincipal AppUserPrincipal principal,
                                                     @PathVariable UUID goalId) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        return ApiResponse.of(service.getProgress(principal.id(), goalId));
    }
}
