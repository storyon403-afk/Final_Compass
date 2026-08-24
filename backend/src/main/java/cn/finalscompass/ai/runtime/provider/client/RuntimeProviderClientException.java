package cn.finalscompass.ai.runtime.provider.client;

/**
 * 表示运行时供应商客户端Exception场景下可识别并向上层传播的失败
 * 维护入口：供应商 HTTP 协议、错误映射或工具调用格式变化时修改这里
 */
public final class RuntimeProviderClientException extends RuntimeException {
  private final String errorCode;
  private final Integer statusCode;
  private final boolean retryable;

  public RuntimeProviderClientException(
      String errorCode, Integer statusCode, boolean retryable, Throwable cause) {
    super("Runtime Provider request failed: " + errorCode, cause);
    this.errorCode = errorCode;
    this.statusCode = statusCode;
    this.retryable = retryable;
  }

  public String errorCode() {
    return errorCode;
  }

  public Integer statusCode() {
    return statusCode;
  }

  public boolean retryable() {
    return retryable;
  }
}
