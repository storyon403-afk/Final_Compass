package cn.finalscompass.ai.runtime.provider.client;

import java.util.List;
import java.util.Map;

public record RuntimeHttpResponse(int statusCode, Map<String, List<String>> headers, String body) {
  public RuntimeHttpResponse {
    headers = Map.copyOf(headers);
  }
}
