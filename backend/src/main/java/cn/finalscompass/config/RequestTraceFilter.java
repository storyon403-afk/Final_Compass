package cn.finalscompass.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 为每次 HTTP 请求生成追踪号，并记录方法、路径、状态码和耗时。 */
@Component
public class RequestTraceFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

  /** 包裹整条过滤器链，在响应头写入 {@code X-Trace-Id}，并在请求结束时记录访问日志。 */
  @Override
  public void doFilter(ServletRequest rawRequest, ServletResponse rawResponse, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) rawRequest;
    HttpServletResponse response = (HttpServletResponse) rawResponse;
    String traceId = UUID.randomUUID().toString().substring(0, 12);
    long started = System.nanoTime();
    response.setHeader("X-Trace-Id", traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - started) / 1_000_000;
      log.info(
          "trace={} method={} path={} status={} duration_ms={}",
          traceId,
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          durationMs);
    }
  }
}
