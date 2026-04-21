package com.basisttha.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ErrorResponse> handleUserException(UserException e, WebRequest request) {
        return build(e.getMessage(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(UserAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistException e, WebRequest request) {
        return build(e.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(UnauthorizedUserException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedUserException e, WebRequest request) {
        return build(e.getMessage(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e, WebRequest request) {
        return build(e.getMessage(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<ErrorResponse> handleOtp(OtpException e, WebRequest request) {
        return build(e.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(TokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleToken(TokenInvalidException e, WebRequest request) {
        return build(e.getMessage(), HttpStatus.UNAUTHORIZED, request);
    }

    /** Handles @Valid / @Validated constraint violations — returns a field→message map. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        var errors = new HashMap<String, String>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ValidationErrorResponse(errors));
    }

    /**
     * Catch-all for any unhandled exception.
     * Logs the full stack trace server-side but returns only a generic message to
     * the client — never expose internal stack traces to callers.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception e, WebRequest request) {
        log.error("Unhandled exception at {}: {}", request.getDescription(false), e.getMessage(), e);
        // Include exception details in dev — change to a generic message before going to prod
        String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
        Throwable cause = e.getCause();
        if (cause != null) detail += " | caused by: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return build(detail, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> build(String message, HttpStatus status, WebRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .errorMessage(message)
                .timestamp(LocalDateTime.now())
                .endpoint(request.getDescription(false).replace("uri=", ""))
                .build();
        return new ResponseEntity<>(body, status);
    }
}
