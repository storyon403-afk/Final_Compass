package cn.finalscompass.ai.credential;

import java.util.Arrays;

/** Short-lived provider credential whose mutable key buffer is cleared on close. */
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

  @Override
  public void close() {
    Arrays.fill(apiKey, '\0');
  }
}
