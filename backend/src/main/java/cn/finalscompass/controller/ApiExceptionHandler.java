package cn.finalscompass.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidInput(Exception exception) {
        String message = exception.getMessage() == null ? "请求参数无效" : exception.getMessage();
        log.warn("request rejected: {}", message);
        return Map.of("error", message);
    }

    /** Returns the application's sanitized reason instead of Spring's generic status phrase. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        String reason = exception.getReason() == null ? "请求暂时无法完成" : exception.getReason();
        log.warn("request failed with status {}: {}", exception.getStatusCode().value(), reason);
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", reason));
    }
}
