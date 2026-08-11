package cn.finalscompass.ai.runtime.provider.client;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record RuntimeHttpRequest(
        URI uri, Duration connectTimeout, Duration requestTimeout,
        Map<String, String> headers, String body, int maximumResponseBytes
) {
    public RuntimeHttpRequest { headers = Map.copyOf(headers); }
}
