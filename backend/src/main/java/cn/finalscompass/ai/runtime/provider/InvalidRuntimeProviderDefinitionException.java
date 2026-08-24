package cn.finalscompass.ai.runtime.provider;

/**
 * 表示Invalid运行时供应商定义Exception场景下可识别并向上层传播的失败
 * 维护入口：供应商、模型、端点定义及匹配规则变化时修改这里
 */
public final class InvalidRuntimeProviderDefinitionException extends RuntimeException {
  private final String providerKey;
  private final String field;
  private final String reasonCode;

  public InvalidRuntimeProviderDefinitionException(
      String providerKey, String field, String reasonCode) {
    super(
        "Invalid runtime provider definition: provider="
            + safe(providerKey)
            + ", field="
            + field
            + ", reason="
            + reasonCode);
    this.providerKey = providerKey;
    this.field = field;
    this.reasonCode = reasonCode;
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }

  public String providerKey() {
    return providerKey;
  }

  public String field() {
    return field;
  }

  public String reasonCode() {
    return reasonCode;
  }
}
