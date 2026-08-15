package cn.finalscompass.controller;

import cn.finalscompass.config.TraceContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 将控制器抛出的常见异常转换为稳定且经过脱敏的 API 错误响应。 */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalidInput(Exception exception) {
    String message = exception.getMessage() == null ? "请求参数无效" : exception.getMessage();
    log.warn("request rejected: {}", message);
    return errorBody(message);
  }

  /** Returns the application's sanitized reason instead of Spring's generic status phrase. */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
    String reason = exception.getReason() == null ? "请求暂时无法完成" : exception.getReason();
    log.warn("request failed with status {}: {}", exception.getStatusCode().value(), reason);
    return ResponseEntity.status(exception.getStatusCode()).body(errorBody(reason));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
    log.error("unhandled request failure", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(errorBody("请求暂时无法完成，请使用追踪号联系管理员"));
  }

  private Map<String, String> errorBody(String message) {
    String traceId = TraceContext.currentTraceId();
    return traceId == null
        ? Map.of("error", message)
        : Map.of("error", message, "traceId", traceId);
  }
}
