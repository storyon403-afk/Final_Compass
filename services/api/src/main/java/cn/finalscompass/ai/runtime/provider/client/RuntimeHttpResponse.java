package cn.finalscompass.ai.runtime.provider.client;

import java.util.List;
import java.util.Map;

/**
 * 运行时HTTPResponse的数据载体，用于在相邻运行时组件之间传递不可变数据
 * 维护入口：供应商 HTTP 协议、错误映射或工具调用格式变化时修改这里
 */
public record RuntimeHttpResponse(int statusCode, Map<String, List<String>> headers, String body) {
  public RuntimeHttpResponse {
    headers = Map.copyOf(headers);
  }
}
