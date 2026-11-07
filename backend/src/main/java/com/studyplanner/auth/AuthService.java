package com.studyplanner.auth;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyplanner.auth.dto.RegisterRequest;
import com.studyplanner.auth.dto.UserResponse;

@Service
public class AuthService {
    private final AuthUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(AuthUserRepository users,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.users = Objects.requireNonNull(users, "users must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.authenticationManager = Objects.requireNonNull(authenticationManager, "authenticationManager must not be null");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String normalizedEmail = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);

        if (users.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("Email đã được sử dụng.");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        AppUser savedUser = users.insertUser(normalizedEmail, hashedPassword);
        return new UserResponse(savedUser.id(), savedUser.email());
    }

    public Authentication authenticate(String email, String rawPassword) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        var authToken = new UsernamePasswordAuthenticationToken(normalizedEmail, rawPassword);
        return authenticationManager.authenticate(authToken);
    }

    public UserResponse getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return new UserResponse(principal.id(), principal.getUsername());
    }
}
