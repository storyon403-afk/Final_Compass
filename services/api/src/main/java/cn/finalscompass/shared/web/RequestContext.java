package cn.finalscompass.shared.web;

import java.time.Instant;

/** 在 HTTP 边界一次性创建的不可变传输上下文 */
public record RequestContext(
    String requestId,
    String traceId,
    Instant receivedAt,
    String method,
    String path,
    String clientAddress) {
  public static final String REQUEST_ATTRIBUTE = RequestContext.class.getName();
}
