package com.studyplanner.auth;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.api.ApiResponse;
import com.studyplanner.api.UnauthenticatedException;
import com.studyplanner.auth.dto.CsrfResponse;
import com.studyplanner.auth.dto.LoginRequest;
import com.studyplanner.auth.dto.RegisterRequest;
import com.studyplanner.auth.dto.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Auth endpoints under /api/v1 (docs/backend-api-rules.md). Logout is handled by the security filter chain. */
@RestController
@RequestMapping("/api/v1")
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

    @GetMapping("/auth/csrf")
    public ApiResponse<CsrfResponse> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = requireCsrfToken(request);
        addCsrfCookie(response, csrfToken.getToken());
        return ApiResponse.of(new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(user));
    }

    @PostMapping("/auth/login")
    public ApiResponse<UserResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        Authentication authentication = authService.authenticate(request.email(), request.password());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();

        // The session was just replaced, so the client needs a fresh CSRF token (also sent as a cookie).
        CsrfToken csrfToken = (CsrfToken) httpRequest.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) httpRequest.getAttribute("_csrf");
        }
        if (csrfToken != null) {
            httpResponse.setHeader(csrfToken.getHeaderName(), csrfToken.getToken());
            addCsrfCookie(httpResponse, csrfToken.getToken());
        }

        return ApiResponse.of(authService.getUser(principal.id()).orElseThrow(UnauthenticatedException::new));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        return ApiResponse.of(authService.getUser(principal.id()).orElseThrow(UnauthenticatedException::new));
    }

    private static CsrfToken requireCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken == null) {
            throw new IllegalStateException("CSRF token is not available in request context");
        }
        return csrfToken;
    }

    private static void addCsrfCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("XSRF-TOKEN", token)
                .path("/")
                .httpOnly(false)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
