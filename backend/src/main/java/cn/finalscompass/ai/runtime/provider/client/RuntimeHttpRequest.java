package cn.finalscompass.ai.runtime.provider.client;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * 运行时HTTP请求的数据载体，用于在相邻运行时组件之间传递不可变数据
 * 维护入口：供应商 HTTP 协议、错误映射或工具调用格式变化时修改这里
 */
public record RuntimeHttpRequest(
    URI uri,
    Duration connectTimeout,
    Duration requestTimeout,
    Map<String, String> headers,
    String body,
    int maximumResponseBytes) {
  public RuntimeHttpRequest {
    headers = Map.copyOf(headers);
  }
}
