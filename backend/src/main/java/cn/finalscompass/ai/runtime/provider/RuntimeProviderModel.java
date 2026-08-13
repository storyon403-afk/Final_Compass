package cn.finalscompass.ai.runtime.provider;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 运行时供应商模型的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：供应商、模型、端点定义及匹配规则变化时修改这里。
 */
public record RuntimeProviderModel(
    long id,
    String key,
    String displayName,
    RuntimeProviderModelStatus status,
    Integer contextWindow,
    Integer maxOutputUnits,
    boolean structuredOutput,
    boolean toolCalling,
    BigDecimal inputUnitPrice,
    BigDecimal outputUnitPrice,
    String currency,
    int routingPriority,
    int routingWeight,
    String configurationJson,
    Set<String> capabilities) {
  public RuntimeProviderModel {
    capabilities = Set.copyOf(capabilities);
  }
}
