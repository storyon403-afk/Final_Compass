package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.runtime.tool.RuntimeToolCallResult;

import java.util.List;

public record RuntimeProviderContinuation(String opaqueState, List<RuntimeToolCallResult> toolResults) {
    public RuntimeProviderContinuation {
        if (opaqueState == null || opaqueState.isBlank() || opaqueState.length() > 10 * 1024 * 1024)
            throw new IllegalArgumentException("Runtime Provider continuation state is invalid");
        toolResults = List.copyOf(toolResults);
        if (toolResults.isEmpty()) throw new IllegalArgumentException("Runtime Tool results are empty");
    }
}
