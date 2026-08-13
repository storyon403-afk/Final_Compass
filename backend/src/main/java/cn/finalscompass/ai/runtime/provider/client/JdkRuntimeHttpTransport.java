package cn.finalscompass.ai.runtime.provider.client;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * 基于 JDK HttpClient 执行受限 HTTP 请求，统一处理超时、响应大小和响应头。
 * 维护入口：代理、重试、TLS 或响应上限等通用网络策略应在这里维护。
 */
@Component
public final class JdkRuntimeHttpTransport implements RuntimeHttpTransport {
  // 发送带限制的 JSON HTTP 请求。先组装协议请求，再通过传输层发送并校验响应；在结束时主动释放资源或擦除敏感数据。
  @Override
  public RuntimeHttpResponse postJson(RuntimeHttpRequest request) {
    validate(request);
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(request.connectTimeout()).build();
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(request.uri())
              .timeout(request.requestTimeout())
              .POST(HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8));
      request.headers().forEach(builder::header);
      HttpResponse<java.io.InputStream> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
      byte[] body;
      try (java.io.InputStream input = response.body()) {
        body = input.readNBytes(request.maximumResponseBytes() + 1);
      }
      if (body.length > request.maximumResponseBytes())
        throw new IllegalStateException("Runtime Provider response exceeds configured limit");
      return new RuntimeHttpResponse(
          response.statusCode(),
          response.headers().map(),
          new String(body, StandardCharsets.UTF_8));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Runtime Provider request interrupted", exception);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Runtime Provider request failed", exception);
    }
  }

  // 校验定义及其关联配置。
  private void validate(RuntimeHttpRequest request) {
    if (request == null
        || request.uri() == null
        || !allowedUri(request.uri())
        || request.connectTimeout() == null
        || request.requestTimeout() == null
        || request.connectTimeout().isNegative()
        || request.connectTimeout().isZero()
        || request.requestTimeout().isNegative()
        || request.requestTimeout().isZero()
        || request.requestTimeout().compareTo(request.connectTimeout()) < 0
        || request.body() == null
        || request.maximumResponseBytes() < 1024
        || request.maximumResponseBytes() > 32 * 1024 * 1024)
      throw new IllegalArgumentException("Runtime HTTP request is invalid");
    request
        .headers()
        .forEach(
            (name, value) -> {
              if (name == null
                  || !name.matches("^[A-Za-z0-9-]{1,80}$")
                  || value == null
                  || value.indexOf('\r') >= 0
                  || value.indexOf('\n') >= 0)
                throw new IllegalArgumentException("Runtime HTTP header is invalid");
            });
  }

  // 校验远端地址是否在安全白名单内。
  private boolean allowedUri(java.net.URI uri) {
    if (uri.getHost() == null || uri.getUserInfo() != null) return false;
    if ("https".equalsIgnoreCase(uri.getScheme())) return true;
    if (!"http".equalsIgnoreCase(uri.getScheme())) return false;
    String host = uri.getHost().toLowerCase();
    return "localhost".equals(host)
        || "127.0.0.1".equals(host)
        || "::1".equals(host)
        || "0:0:0:0:0:0:0:1".equals(host);
  }
}
