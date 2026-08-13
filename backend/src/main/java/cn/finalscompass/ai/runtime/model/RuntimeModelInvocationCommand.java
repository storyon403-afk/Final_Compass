package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.runtime.provider.RuntimeProviderType;
import cn.finalscompass.ai.runtime.tool.RuntimeToolSpecification;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 运行时模型调用Command的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：统一模型命令、回退和执行结果契约变化时修改这里。
 */
public record RuntimeModelInvocationCommand(
    long providerId,
    String providerKey,
    RuntimeProviderType providerType,
    String adapterKey,
    long providerModelId,
    String modelKey,
    long endpointId,
    String endpointKey,
    String baseUrl,
    String credentialSource,
    String skillKey,
    String skillVersion,
    String systemInstruction,
    String userInput,
    String contextJson,
    String outputContract,
    String outputSchemaJson,
    Set<String> allowedTools,
    List<RuntimeToolSpecification> toolSpecifications,
    Set<String> modalities,
    boolean structuredOutputRequired,
    BigDecimal inputUnitPrice,
    BigDecimal outputUnitPrice,
    String pricingCurrency,
    int connectTimeoutMs,
    int timeoutMs) {
  public RuntimeModelInvocationCommand {
    allowedTools = Set.copyOf(allowedTools);
    toolSpecifications = List.copyOf(toolSpecifications);
    modalities = Set.copyOf(modalities);
  }
}
