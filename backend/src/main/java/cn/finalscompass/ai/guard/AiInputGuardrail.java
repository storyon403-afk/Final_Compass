package cn.finalscompass.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Performs deterministic input checks before planning or provider invocation. */
@Component
public class AiInputGuardrail {
    public GuardedInput inspect(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("请输入需要分析的内容");
        String input = value.strip();
        if (input.codePoints().anyMatch(code -> Character.isISOControl(code) && code != '\n' && code != '\r' && code != '\t')) {
            throw new IllegalArgumentException("输入包含不支持的控制字符");
        }
        String lower = input.toLowerCase(Locale.ROOT);
        boolean instructionInjection = List.of("忽略系统", "忽略之前", "system prompt", "developer message",
                        "reveal your instructions", "调用任意工具")
                .stream().anyMatch(lower::contains);
        return new GuardedInput(input, instructionInjection ? List.of("UNTRUSTED_INSTRUCTION") : List.of());
    }

    public record GuardedInput(String text, List<String> riskFlags) {}
}
