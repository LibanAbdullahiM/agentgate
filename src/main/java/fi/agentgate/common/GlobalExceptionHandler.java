package fi.agentgate.common;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(String code, String message, Instant timestamp, List<String> details) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorBody(e.getCode(), e.getMessage(), Instant.now(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorBody("VALIDATION_ERROR", "Request validation failed", Instant.now(), details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("MALFORMED_REQUEST", "Request body is missing or malformed", Instant.now(), List.of()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorBody> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("MISSING_HEADER", "Missing required header: " + e.getHeaderName(), Instant.now(), List.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> handleIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("CONFLICT", "The request conflicts with existing data (possible duplicate)", Instant.now(), List.of()));
    }
}
