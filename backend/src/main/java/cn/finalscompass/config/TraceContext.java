package cn.finalscompass.config;

import java.util.Map;
import org.slf4j.MDC;

/** Shared MDC context used to correlate HTTP requests and AI executions. */
public final class TraceContext {
  public static final String HTTP_TRACE_ID = "traceId";
  public static final String AI_TRACE_ID = "aiTraceId";

  private TraceContext() {}

  public static String currentTraceId() {
    return MDC.get(HTTP_TRACE_ID);
  }

  public static Map<String, String> capture() {
    Map<String, String> context = MDC.getCopyOfContextMap();
    return context == null ? Map.of() : Map.copyOf(context);
  }

  public static void runWith(Map<String, String> context, Runnable work) {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    try {
      if (context == null || context.isEmpty()) MDC.clear();
      else MDC.setContextMap(context);
      work.run();
    } finally {
      if (previous == null || previous.isEmpty()) MDC.clear();
      else MDC.setContextMap(previous);
    }
  }
}
