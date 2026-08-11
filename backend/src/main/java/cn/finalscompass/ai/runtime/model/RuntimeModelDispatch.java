package cn.finalscompass.ai.runtime.model;

import java.util.List;

public record RuntimeModelDispatch(
        String executorType, RuntimeModelInvocationCommand primary,
        List<RuntimeModelInvocationCommand> fallbacks
) {
    public RuntimeModelDispatch { fallbacks = List.copyOf(fallbacks); }
}
