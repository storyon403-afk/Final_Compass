package cn.finalscompass.ai.runtime.provider.client;

public interface RuntimeHttpTransport {
  RuntimeHttpResponse postJson(RuntimeHttpRequest request);
}
