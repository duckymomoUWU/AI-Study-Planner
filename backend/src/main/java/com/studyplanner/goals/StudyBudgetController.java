package com.studyplanner.goals;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.api.*;
import com.studyplanner.auth.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/study-budget")
public class StudyBudgetController {
    private final StudyBudgetService service;
    public StudyBudgetController(StudyBudgetService service){this.service=service;}
    @GetMapping public ApiResponse<StudyBudgetService.Settings> get(@AuthenticationPrincipal AppUserPrincipal user,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to) {
        return ApiResponse.of(service.get(owner(user),from,to));
    }
    @PutMapping public ApiResponse<StudyBudgetService.Settings> save(@AuthenticationPrincipal AppUserPrincipal user,@RequestBody JsonNode body) {
        return ApiResponse.of(service.save(owner(user),body));
    }
    private static UUID owner(AppUserPrincipal user){if(user==null)throw new UnauthenticatedException();return user.id();}
}
