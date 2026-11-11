package com.studyplanner.calendar;

import com.studyplanner.api.*;
import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.goals.PlanException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {
    private final CalendarService service; private final GoogleCalendarClient google; private final Clock clock;
    public CalendarController(CalendarService service,GoogleCalendarClient google,Clock clock) { this.service=service;this.google=google;this.clock=clock; }
    @GetMapping("/status") public ApiResponse<?> status(@AuthenticationPrincipal AppUserPrincipal user) { return ApiResponse.of(service.status(owner(user))); }
    @GetMapping("/calendars") public ApiResponse<?> calendars(@AuthenticationPrincipal AppUserPrincipal user) { return ApiResponse.of(service.calendars(owner(user))); }
    @PatchMapping("/settings") public ApiResponse<?> settings(@AuthenticationPrincipal AppUserPrincipal user,@RequestBody CalendarScheduling.Settings body) { return ApiResponse.of(service.settings(owner(user),body)); }
    @DeleteMapping("/connection") public ResponseEntity<Void> disconnect(@AuthenticationPrincipal AppUserPrincipal user) { service.disconnect(owner(user)); return ResponseEntity.noContent().build(); }
    public record PreviewRequest(LocalDate from,UUID goalId) {}
    @PostMapping("/previews") public ResponseEntity<?> preview(@AuthenticationPrincipal AppUserPrincipal user,@RequestBody PreviewRequest body) { return ResponseEntity.status(201).body(ApiResponse.of(service.preview(owner(user),body.from(),body.goalId()))); }
    @PostMapping("/previews/{id}/confirm") public ApiResponse<?> confirm(@AuthenticationPrincipal AppUserPrincipal user,@PathVariable UUID id) { return ApiResponse.of(service.confirm(owner(user),id)); }
    record OAuthState(UUID owner,String nonce,String verifier,Instant expires) implements java.io.Serializable {}
    @PostMapping("/connection") public ApiResponse<?> connect(@AuthenticationPrincipal AppUserPrincipal user,HttpSession session) {
        UUID owner=owner(user); byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String verifier=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),nonce=UUID.randomUUID().toString();
        String challenge;
        try { challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
        String url=google.authorizationUrl(nonce,challenge);
        session.setAttribute("calendar.oauth",new OAuthState(owner,nonce,verifier,clock.instant().plusSeconds(600)));
        return ApiResponse.of(Map.of("url",url));
    }
    @GetMapping("/oauth/callback") public ResponseEntity<Void> callback(@AuthenticationPrincipal AppUserPrincipal user,HttpSession session,
            @RequestParam(required=false) String state,@RequestParam(required=false) String code,@RequestParam(required=false) String error) {
        UUID owner=owner(user); Object saved=session.getAttribute("calendar.oauth"); session.removeAttribute("calendar.oauth");
        if(!(saved instanceof OAuthState flow) || !flow.owner().equals(owner) || state==null || !MessageDigest.isEqual(flow.nonce().getBytes(StandardCharsets.UTF_8),state.getBytes(StandardCharsets.UTF_8)) || !flow.expires().isAfter(clock.instant()))
            throw new PlanException(400,"CALENDAR_OAUTH_STATE_INVALID","Phiên kết nối không hợp lệ hoặc đã hết hạn. Hãy thử kết nối lại.");
        boolean success=false;
        if(error==null && code!=null && !code.isBlank() && code.length()<4096) {
            try { google.exchange(owner,code,flow.verifier()); success=true; } catch(PlanException ex) { /* No codes/tokens/provider response in logs or redirects. */ }
        }
        return ResponseEntity.status(303).location(URI.create(google.returnUrl(success))).build();
    }
    private static UUID owner(AppUserPrincipal user) { if(user==null) throw new UnauthenticatedException(); return user.id(); }
}
