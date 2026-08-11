package cn.finalscompass.ai.runtime.provider;

public record RuntimeProviderEndpoint(
        long id, String key, String baseUrl, String region, int priority, int weight,
        RuntimeProviderStatus status, int connectTimeoutMs, int requestTimeoutMs,
        String healthCheckPath, String configurationJson
) {}
