package com.studyplanner.auth;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.auth.dto.AuthErrorResponse;
import com.studyplanner.auth.dto.LoginRequest;
import com.studyplanner.auth.dto.RegisterRequest;
import com.studyplanner.auth.dto.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
public class AuthController {
    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public AuthController(AuthService authService,
                          SecurityContextRepository securityContextRepository,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.securityContextRepository = Objects.requireNonNull(securityContextRepository, "securityContextRepository must not be null");
        this.sessionAuthenticationStrategy = Objects.requireNonNull(sessionAuthenticationStrategy, "sessionAuthenticationStrategy must not be null");
    }

    @GetMapping({"/api/v1/auth/csrf", "/api/auth/csrf"})
    public ResponseEntity<Map<String, Object>> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken == null) {
            throw new IllegalStateException("CSRF token is not available in request context");
        }

        String tokenValue = csrfToken.getToken();

        ResponseCookie cookie = ResponseCookie.from("XSRF-TOKEN", tokenValue)
                .path("/")
                .httpOnly(false)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(Map.of(
                "data", Map.of(
                        "headerName", csrfToken.getHeaderName(),
                        "parameterName", csrfToken.getParameterName(),
                        "token", tokenValue
                )
        ));
    }

    @PostMapping({"/api/v1/auth/register", "/api/auth/register"})
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", user));
    }

    @PostMapping({"/api/v1/auth/login", "/api/auth/login"})
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        Authentication authentication = authService.authenticate(request.email(), request.password());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        UserResponse responseBody = new UserResponse(principal.id(), principal.getUsername());

        CsrfToken csrfToken = (CsrfToken) httpRequest.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) httpRequest.getAttribute("_csrf");
        }
        if (csrfToken != null) {
            httpResponse.setHeader("X-CSRF-TOKEN", csrfToken.getToken());
            ResponseCookie cookie = ResponseCookie.from("XSRF-TOKEN", csrfToken.getToken())
                    .path("/")
                    .httpOnly(false)
                    .sameSite("Lax")
                    .build();
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return ResponseEntity.ok(Map.of("data", responseBody));
    }

    @GetMapping({"/api/v1/me", "/api/auth/me"})
    public ResponseEntity<?> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthErrorResponse("UNAUTHORIZED", "Bạn cần đăng nhập."));
        }
        return ResponseEntity.ok(Map.of("data", new UserResponse(principal.id(), principal.getUsername())));
    }
}
