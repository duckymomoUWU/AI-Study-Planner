package com.studyplanner.auth;

import java.util.Locale;
import java.util.Objects;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AuthUserRepository users;

    public AppUserDetailsService(AuthUserRepository users) {
        this.users = Objects.requireNonNull(users, "users must not be null");
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        AppUser user = users.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found with email: " + normalizedEmail));
        return new AppUserPrincipal(user.id(), user.email(), user.passwordHash());
    }
}
