package com.studyplanner.goals;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.api.ApiResponse;
import com.studyplanner.api.UnauthenticatedException;
import com.studyplanner.auth.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/goals")
public class SyllabusController {
    private final SyllabusService service;
    public SyllabusController(SyllabusService service) { this.service = service; }
    @PostMapping("/parse-syllabus")
    public ApiResponse<SyllabusService.Result> extract(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody JsonNode body) {
        if (principal == null) throw new UnauthenticatedException();
        return ApiResponse.of(service.extract(body));
    }
}
