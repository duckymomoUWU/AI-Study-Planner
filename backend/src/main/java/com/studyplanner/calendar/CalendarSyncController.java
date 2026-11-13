package com.studyplanner.calendar;

import java.util.UUID;
import com.studyplanner.api.*;
import com.studyplanner.auth.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/calendar/sync")
public class CalendarSyncController {
    private final CalendarSyncService service;
    public CalendarSyncController(CalendarSyncService service){this.service=service;}
    @GetMapping public ApiResponse<?> status(@AuthenticationPrincipal AppUserPrincipal user){return ApiResponse.of(service.summary(owner(user)));}
    @PostMapping("/retry") public ApiResponse<?> retry(@AuthenticationPrincipal AppUserPrincipal user){return ApiResponse.of(service.retry(owner(user)));}
    private static UUID owner(AppUserPrincipal user){if(user==null)throw new UnauthenticatedException();return user.id();}
}
