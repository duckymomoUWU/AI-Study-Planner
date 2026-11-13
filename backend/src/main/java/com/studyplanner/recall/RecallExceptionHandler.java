package com.studyplanner.recall;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.studyplanner.recall")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RecallExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RecallExceptionHandler.class);

    @ExceptionHandler(RecallException.class)
    public ResponseEntity<RecallDTOs.ErrorResponse> handleRecallException(RecallException ex) {
        log.warn("RecallException caught [{}]: {}", ex.getCode(), ex.getMessage());
        RecallDTOs.ErrorResponse body = new RecallDTOs.ErrorResponse(
                new RecallDTOs.ErrorResponse.ErrorDetail(
                        ex.getCode(),
                        ex.getMessage(),
                        ex.getDetails().isEmpty() ? null : ex.getDetails(),
                        ex.getContext().isEmpty() ? null : ex.getContext()
                )
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RecallDTOs.ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<RecallException.FieldErrorItem> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String issue = fieldError.getCode() != null ? fieldError.getCode().toLowerCase() : "invalid";
            details.add(new RecallException.FieldErrorItem(fieldError.getField(), issue));
        }
        RecallDTOs.ErrorResponse body = new RecallDTOs.ErrorResponse(
                new RecallDTOs.ErrorResponse.ErrorDetail(
                        "VALIDATION_ERROR",
                        "Dữ liệu yêu cầu không hợp lệ.",
                        details,
                        null
                )
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RecallDTOs.ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception in recall module: {}", ex.getMessage(), ex);
        RecallDTOs.ErrorResponse body = new RecallDTOs.ErrorResponse(
                new RecallDTOs.ErrorResponse.ErrorDetail(
                        "GATEWAY_ERROR",
                        "Không thể kết nối đến dịch vụ tạo flashcard. Vui lòng thử lại sau.",
                        null,
                        null
                )
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }
}
