package cn.finalscompass.ai.runtime.provider;

/**
 * 运行时供应商端点的数据载体，用于在相邻运行时组件之间传递不可变数据
 * 维护入口：供应商、模型、端点定义及匹配规则变化时修改这里
 */
public record RuntimeProviderEndpoint(
    long id,
    String key,
    String baseUrl,
    String region,
    int priority,
    int weight,
    RuntimeProviderStatus status,
    int connectTimeoutMs,
    int requestTimeoutMs,
    String healthCheckPath,
    String configurationJson) {}
