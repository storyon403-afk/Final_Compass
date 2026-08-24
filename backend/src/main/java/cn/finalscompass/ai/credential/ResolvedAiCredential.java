package cn.finalscompass.ai.credential;

import java.util.Arrays;

/**
 * 一次模型调用最终解析出的供应商、模型和 API Key；关闭对象时会擦除内存中的密钥
 * 维护入口：凭据选择规则应改 AiCredentialResolver；这里只维护解析结果及敏感数据生命周期
 */
public final class ResolvedAiCredential implements AutoCloseable {
  private final String provider;
  private final String model;
  private final AiCredentialSource source;
  private final char[] apiKey;

  public ResolvedAiCredential(
      String provider, String model, AiCredentialSource source, char[] apiKey) {
    this.provider = provider;
    this.model = model;
    this.source = source;
    this.apiKey = apiKey;
  }

  public String provider() {
    return provider;
  }

  public String model() {
    return model;
  }

  public AiCredentialSource source() {
    return source;
  }

  public char[] apiKey() {
    return apiKey;
  }

  // 清除内存中的敏感凭据。在结束时主动释放资源或擦除敏感数据
  @Override
  public void close() {
    Arrays.fill(apiKey, '\0');
  }
}
