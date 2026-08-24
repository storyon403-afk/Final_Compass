package cn.finalscompass.ai.runtime.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeToolExecutorTest {
    @Test
    void executesOnlyRegisteredAllowedAndPermittedToolWithValidSchemas() {
        RuntimeToolDefinition definition = definition();
        RuntimeToolDefinitionRepository repository = repository(definition);
        RuntimeToolHandler handler = new RuntimeToolHandler() {
            @Override public String executorKey() { return "knowledge-search-handler"; }
            @Override public String invoke(RuntimeToolDefinition ignored,
                                           RuntimeToolExecutionContext context, String argumentsJson) {
                return "{\"matches\":[\"chapter-1\"]}";
            }
        };
        RuntimeToolExecutor executor = new RuntimeToolExecutor(repository, List.of(handler), new ObjectMapper());
        RuntimeToolCallResult result = executor.execute(context(Set.of("Knowledge.search"), Set.of("KNOWLEDGE_READ")),
                new RuntimeToolCall("call_1", "Knowledge.search", "{\"query\":\"calculus\"}"));
        assertTrue(result.success());
        assertEquals("Knowledge.search", result.toolKey());
        assertTrue(result.outputJson().contains("chapter-1"));
    }

    @Test
    void rejectsCallsOutsideAllowlistOrPermissionBeforeHandler() {
        int[] calls = {0};
        RuntimeToolHandler handler = new RuntimeToolHandler() {
            @Override public String executorKey() { return "knowledge-search-handler"; }
            @Override public String invoke(RuntimeToolDefinition definition,
                                           RuntimeToolExecutionContext context, String argumentsJson) {
                calls[0]++;
                return "{\"matches\":[]}";
            }
        };
        RuntimeToolExecutor executor = new RuntimeToolExecutor(repository(definition()), List.of(handler),
                new ObjectMapper());
        RuntimeToolCall call = new RuntimeToolCall("call_2", "Knowledge.search", "{\"query\":\"x\"}");
        assertThrows(SecurityException.class, () -> executor.execute(context(Set.of(), Set.of()), call));
        assertThrows(SecurityException.class,
                () -> executor.execute(context(Set.of("Knowledge.search"), Set.of()), call));
        assertEquals(0, calls[0]);
    }

    @Test
    void rejectsInvalidArgumentsAndOversizedOrInvalidResults() {
        RuntimeToolDefinition definition = definition();
        RuntimeToolHandler invalid = new RuntimeToolHandler() {
            @Override public String executorKey() { return "knowledge-search-handler"; }
            @Override public String invoke(RuntimeToolDefinition ignored,
                                           RuntimeToolExecutionContext context, String argumentsJson) {
                return "{\"unexpected\":true}";
            }
        };
        RuntimeToolExecutor executor = new RuntimeToolExecutor(repository(definition), List.of(invalid),
                new ObjectMapper());
        var context = context(Set.of("Knowledge.search"), Set.of("KNOWLEDGE_READ"));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(context,
                new RuntimeToolCall("call_3", "Knowledge.search", "{}")));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(context,
                new RuntimeToolCall("call_4", "Knowledge.search", "{\"query\":\"x\",\"extra\":1}")));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(context,
                new RuntimeToolCall("call_4b", "Knowledge.search", "{\"query\":12}")));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(context,
                new RuntimeToolCall("call_5", "Knowledge.search", "{\"query\":\"x\"}")));
    }

    private RuntimeToolDefinition definition() {
        return new RuntimeToolDefinition(1, "Knowledge.search", "Knowledge Search", "Search approved knowledge",
                "1.0.0", RuntimeToolTransportType.INTERNAL, "knowledge-search-handler",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},"
                        + "\"required\":[\"query\"],\"additionalProperties\":false}",
                "{\"type\":\"object\",\"properties\":{\"matches\":{\"type\":\"array\"}},"
                        + "\"required\":[\"matches\"],\"additionalProperties\":false}",
                Set.of("KNOWLEDGE_READ"), "{}", 5000, 1024);
    }
    private RuntimeToolDefinitionRepository repository(RuntimeToolDefinition definition) {
        return new RuntimeToolDefinitionRepository() {
            @Override public Optional<RuntimeToolDefinition> findActiveByKey(String toolKey) {
                return definition.toolKey().equals(toolKey) ? Optional.of(definition) : Optional.empty();
            }
            @Override public List<RuntimeToolDefinition> findActiveByKeys(java.util.Collection<String> toolKeys) {
                return toolKeys.contains(definition.toolKey()) ? List.of(definition) : List.of();
            }
        };
    }
    private RuntimeToolExecutionContext context(Set<String> tools, Set<String> permissions) {
        return new RuntimeToolExecutionContext(1, 2, 3, "course-help", tools, permissions);
    }
}
