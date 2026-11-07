package com.studyplanner.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.studyplanner.auth.dto.AuthErrorResponse;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<AuthErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthErrorResponse("EMAIL_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<AuthErrorResponse> handleBadCredentials(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse("INVALID_CREDENTIALS", "Email hoặc mật khẩu không chính xác."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<AuthErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse("UNAUTHORIZED", "Bạn cần đăng nhập."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AuthErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new AuthErrorResponse("CSRF_INVALID", "Yêu cầu không hợp lệ hoặc phiên đã hết hạn."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<AuthErrorResponse.FieldErrorItem> fieldErrors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String issue = fieldError.getCode() != null ? fieldError.getCode().toLowerCase() : "invalid";
            fieldErrors.add(new AuthErrorResponse.FieldErrorItem(
                    fieldError.getField(),
                    issue,
                    fieldError.getDefaultMessage()
            ));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new AuthErrorResponse("VALIDATION_ERROR", "Dữ liệu yêu cầu không hợp lệ.", fieldErrors));
    }
}
