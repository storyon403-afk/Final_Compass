package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderType;

import cn.finalscompass.ai.runtime.trace.CreateRuntimeExecution;
import cn.finalscompass.ai.runtime.trace.CreateRuntimeExecutionNode;
import cn.finalscompass.ai.runtime.trace.CreateRuntimeProviderInvocation;
import cn.finalscompass.ai.runtime.trace.RuntimeExecutionNodeStatus;
import cn.finalscompass.ai.runtime.trace.RuntimeExecutionStatus;
import cn.finalscompass.ai.runtime.trace.RuntimeExecutionTraceStore;
import cn.finalscompass.ai.runtime.trace.RuntimeProviderInvocationResult;
import cn.finalscompass.ai.runtime.trace.RuntimeProviderInvocationStatus;
import cn.finalscompass.ai.runtime.provider.client.RuntimeProviderClientRegistry;
import cn.finalscompass.ai.runtime.provider.client.RuntimeProviderProtocolClient;
import cn.finalscompass.ai.runtime.provider.client.RuntimeProviderClientResult;
import cn.finalscompass.ai.runtime.provider.client.RuntimeBinaryInput;
import cn.finalscompass.ai.runtime.provider.client.RuntimeProviderContinuation;
import cn.finalscompass.ai.runtime.tool.RuntimeToolCall;
import cn.finalscompass.ai.runtime.tool.RuntimeToolDefinition;
import cn.finalscompass.ai.runtime.tool.RuntimeToolDefinitionRepository;
import cn.finalscompass.ai.runtime.tool.RuntimeToolExecutionContext;
import cn.finalscompass.ai.runtime.tool.RuntimeToolExecutor;
import cn.finalscompass.ai.runtime.tool.RuntimeToolHandler;
import cn.finalscompass.ai.runtime.tool.RuntimeToolSpecification;
import cn.finalscompass.ai.runtime.tool.RuntimeToolTransportType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRuntimeModelClientGatewayTest {
    @Test
    void tracesFailureUsesFallbackAndClearsEveryCredentialLease() {
        RecordingTraceStore traces = new RecordingTraceStore();
        LegacyRuntimeModelClientGateway client = new LegacyRuntimeModelClientGateway(traces,
                new RuntimeProviderClientRegistry(List.of(
                        staticClient("first-adapter-v1", true, null),
                        staticClient("second-adapter-v1", false, "ok"))));
        char[] firstKey = "first-secret".toCharArray();
        char[] secondKey = "second-secret".toCharArray();
        RuntimeModelDispatch dispatch = new RuntimeModelDispatch("LLM_PROMPT",
                command(1, "first", "first-model"), List.of(command(2, "second", "second-model")));

        RuntimeModelExecutionResult result = client.execute(99, dispatch, command -> {
            char[] key = "first".equals(command.providerKey()) ? firstKey : secondKey;
            return new ResolvedAiCredential(command.providerKey(), command.modelKey(),
                    AiCredentialSource.PLATFORM, key);
        });

        assertEquals("second", result.providerKey());
        assertEquals("ok", result.content());
        assertEquals(List.of(RuntimeProviderInvocationStatus.RUNNING, RuntimeProviderInvocationStatus.FAILED,
                        RuntimeProviderInvocationStatus.RUNNING, RuntimeProviderInvocationStatus.SUCCEEDED),
                traces.transitions);
        assertEquals(traces.createdIds.getFirst(), traces.commands.get(1).fallbackFromId());
        assertTrue(allZero(firstKey));
        assertTrue(allZero(secondKey));
        assertEquals(new BigDecimal("0.70000000"), traces.results.getLast().estimatedCost());
    }

    @Test
    void rejectsCredentialForDifferentCandidateBeforeAdapterCall() {
        int[] calls = {0};
        RuntimeProviderProtocolClient adapter = new RuntimeProviderProtocolClient() {
            @Override public String adapterKey() { return "first-adapter-v1"; }
            @Override public RuntimeProviderClientResult invoke(RuntimeModelInvocationCommand command,
                    ResolvedAiCredential credential, RuntimeBinaryInput binaryInput) {
                calls[0]++;
                return new RuntimeProviderClientResult("ok", 30, 20, false, null, List.of(), null);
            }
        };
        RecordingTraceStore traces = new RecordingTraceStore();
        LegacyRuntimeModelClientGateway client = new LegacyRuntimeModelClientGateway(traces,
                new RuntimeProviderClientRegistry(List.of(adapter)));
        RuntimeModelDispatch dispatch = new RuntimeModelDispatch("LLM_PROMPT",
                command(1, "first", "first-model"), List.of());

        assertThrows(RuntimeModelExecutionException.class, () -> client.execute(99, dispatch,
                command -> new ResolvedAiCredential("second", command.modelKey(),
                        AiCredentialSource.PLATFORM, "mismatch-secret".toCharArray())));
        assertEquals(0, calls[0]);
        assertEquals(RuntimeProviderInvocationStatus.FAILED, traces.transitions.getLast());
    }

    @Test
    void nativeClientExecutesAndRecordsProviderRequestId() {
        RecordingTraceStore traces = new RecordingTraceStore();
        LegacyRuntimeModelClientGateway client = new LegacyRuntimeModelClientGateway(traces,
                new RuntimeProviderClientRegistry(List.of(
                        staticClient("first-adapter-v1", false, "native"))));

        RuntimeModelExecutionResult result = client.execute(99,
                new RuntimeModelDispatch("LLM_PROMPT",
                        command(1, "first", "first-model"), List.of()),
                command -> new ResolvedAiCredential(command.providerKey(), command.modelKey(),
                        AiCredentialSource.PLATFORM, "native-secret".toCharArray()));

        assertEquals("native", result.content());
        assertEquals("native-request-id", traces.results.getLast().providerRequestId());
    }

    @Test
    void unregisteredAdapterKeyFailsWithoutExecuting() {
        RecordingTraceStore traces = new RecordingTraceStore();
        LegacyRuntimeModelClientGateway client = new LegacyRuntimeModelClientGateway(traces,
                new RuntimeProviderClientRegistry(List.of()));
        RuntimeModelDispatch dispatch = new RuntimeModelDispatch("LLM_PROMPT",
                command(1, "first", "first-model"), List.of());

        assertThrows(RuntimeModelExecutionException.class, () -> client.execute(99, dispatch,
                command -> new ResolvedAiCredential(command.providerKey(), command.modelKey(),
                        AiCredentialSource.PLATFORM, "secret".toCharArray())));
        assertEquals(RuntimeProviderInvocationStatus.FAILED, traces.transitions.getLast());
    }

    @Test
    void executesBoundedToolConversationAndTracesOnlyMetadata() {
        RecordingTraceStore traces = new RecordingTraceStore();
        RuntimeProviderProtocolClient nativeClient = new RuntimeProviderProtocolClient() {
            @Override public String adapterKey() { return "first-adapter-v1"; }
            @Override public RuntimeProviderClientResult invoke(RuntimeModelInvocationCommand command,
                    ResolvedAiCredential credential, RuntimeBinaryInput binaryInput) {
                return new RuntimeProviderClientResult("", 5, 3, false, "request-1",
                        List.of(new RuntimeToolCall("call_1", "Knowledge.search", "{\"query\":\"x\"}")),
                        "state-1");
            }
            @Override public RuntimeProviderClientResult continueWithToolResults(
                    RuntimeModelInvocationCommand command, ResolvedAiCredential credential,
                    RuntimeProviderContinuation continuation) {
                assertEquals("{\"matches\":[]}", continuation.toolResults().getFirst().outputJson());
                return new RuntimeProviderClientResult("final", 4, 2, false, "request-2", List.of(), null);
            }
        };
        RuntimeToolDefinition definition = new RuntimeToolDefinition(1, "Knowledge.search", "Search", "Search",
                "1.0.0", RuntimeToolTransportType.INTERNAL, "search-handler", objectSchema(), objectSchema(),
                Set.of("KNOWLEDGE_READ"), "{}", 5000, 1024);
        RuntimeToolDefinitionRepository repository = new RuntimeToolDefinitionRepository() {
            @Override public Optional<RuntimeToolDefinition> findActiveByKey(String key) {
                return Optional.of(definition);
            }
            @Override public List<RuntimeToolDefinition> findActiveByKeys(java.util.Collection<String> keys) {
                return List.of(definition);
            }
        };
        RuntimeToolHandler handler = new RuntimeToolHandler() {
            @Override public String executorKey() { return "search-handler"; }
            @Override public String invoke(RuntimeToolDefinition ignored,
                                           RuntimeToolExecutionContext context, String argumentsJson) {
                return "{\"matches\":[]}";
            }
        };
        var client = new LegacyRuntimeModelClientGateway(traces,
                new RuntimeProviderClientRegistry(List.of(nativeClient)),
                new RuntimeToolExecutor(repository, List.of(handler), new ObjectMapper()));
        RuntimeModelInvocationCommand command = toolCommand();
        RuntimeModelExecutionResult result = client.executeWithTools(
                new RuntimeModelDispatch("LLM_PROMPT", command, List.of()),
                ignored -> new ResolvedAiCredential("first", "first-model", AiCredentialSource.PLATFORM,
                        "secret".toCharArray()), null,
                new RuntimeToolExecutionContext(10, 99, 7, "course-help",
                        Set.of("Knowledge.search"), Set.of("KNOWLEDGE_READ")), 3);
        assertEquals("final", result.content());
        assertEquals(9, result.inputUnits());
        assertEquals(5, result.outputUnits());
        assertEquals(List.of("TOOL_CALL_REQUESTED", "TOOL_CALL_COMPLETED", "TOOL_MODEL_CONTINUED"),
                traces.eventTypes);
        assertTrue(traces.eventPayloads.stream().noneMatch(value -> value.contains("matches")));
    }

    @Test
    void stopsAtRoundLimitWithoutFallingBackAfterToolExecution() {
        RecordingTraceStore traces = new RecordingTraceStore();
        RuntimeProviderProtocolClient looping = new RuntimeProviderProtocolClient() {
            @Override public String adapterKey() { return "first-adapter-v1"; }
            @Override public RuntimeProviderClientResult invoke(RuntimeModelInvocationCommand command,
                    ResolvedAiCredential credential, RuntimeBinaryInput binaryInput) {
                return toolCall("call_1", "state-1");
            }
            @Override public RuntimeProviderClientResult continueWithToolResults(
                    RuntimeModelInvocationCommand command, ResolvedAiCredential credential,
                    RuntimeProviderContinuation continuation) {
                return toolCall("call_2", "state-2");
            }
            private RuntimeProviderClientResult toolCall(String id, String state) {
                return new RuntimeProviderClientResult("", 1, 1, false, null,
                        List.of(new RuntimeToolCall(id, "Knowledge.search", "{}")), state);
            }
        };
        int[] fallbackCalls = {0};
        RuntimeProviderProtocolClient fallback = new RuntimeProviderProtocolClient() {
            @Override public String adapterKey() { return "second-adapter-v1"; }
            @Override public RuntimeProviderClientResult invoke(RuntimeModelInvocationCommand command,
                    ResolvedAiCredential credential, RuntimeBinaryInput binaryInput) {
                fallbackCalls[0]++;
                return new RuntimeProviderClientResult("fallback", 1, 1, false, null, List.of(), null);
            }
        };
        RuntimeToolDefinition definition = new RuntimeToolDefinition(1, "Knowledge.search", "Search", "Search",
                "1.0.0", RuntimeToolTransportType.INTERNAL, "handler", objectSchema(), objectSchema(),
                Set.of(), "{}", 5000, 1024);
        RuntimeToolDefinitionRepository repository = new RuntimeToolDefinitionRepository() {
            @Override public Optional<RuntimeToolDefinition> findActiveByKey(String key) {
                return Optional.of(definition);
            }
            @Override public List<RuntimeToolDefinition> findActiveByKeys(java.util.Collection<String> keys) {
                return List.of(definition);
            }
        };
        RuntimeToolHandler handler = new RuntimeToolHandler() {
            @Override public String executorKey() { return "handler"; }
            @Override public String invoke(RuntimeToolDefinition definition,
                                           RuntimeToolExecutionContext context, String argumentsJson) { return "{}"; }
        };
        var client = new LegacyRuntimeModelClientGateway(traces,
                new RuntimeProviderClientRegistry(List.of(looping, fallback)),
                new RuntimeToolExecutor(repository, List.of(handler), new ObjectMapper()));
        RuntimeModelInvocationCommand first = toolCommand();
        RuntimeModelInvocationCommand second = new RuntimeModelInvocationCommand(
                2, "second", RuntimeProviderType.API, "second-adapter-v1", 12, "second-model",
                22, "default", "https://example.com", "PLATFORM", "course-help", "1.0.0",
                "system", "question", "{}", "output", "{}", first.allowedTools(),
                first.toolSpecifications(), Set.of("TEXT"), false, null, null, null, 1000, 30000);
        assertThrows(RuntimeModelExecutionException.class, () -> client.executeWithTools(
                new RuntimeModelDispatch("LLM_PROMPT", first, List.of(second)),
                command -> new ResolvedAiCredential(command.providerKey(), command.modelKey(),
                        AiCredentialSource.PLATFORM, "secret".toCharArray()), null,
                new RuntimeToolExecutionContext(10, 99, 7, "course-help",
                        Set.of("Knowledge.search"), Set.of()), 1));
        assertEquals(0, fallbackCalls[0]);
        assertEquals(1, traces.createdIds.size());
    }

    private RuntimeProviderProtocolClient staticClient(String adapterKey, boolean fail, String content) {
        return new RuntimeProviderProtocolClient() {
            @Override public String adapterKey() { return adapterKey; }
            @Override public RuntimeProviderClientResult invoke(RuntimeModelInvocationCommand command,
                    ResolvedAiCredential credential, RuntimeBinaryInput binaryInput) {
                if (fail) throw new IllegalStateException("simulated failure");
                return new RuntimeProviderClientResult(content, 30, 20, false, "native-request-id", List.of(), null);
            }
        };
    }

    private RuntimeModelInvocationCommand command(long id, String provider, String model) {
        return new RuntimeModelInvocationCommand(id, provider, RuntimeProviderType.API, provider + "-adapter-v1",
                id + 10, model, id + 20, "default", "https://example.com", "PLATFORM",
                "course-help", "1.0.0", "system", "question", "{}", "output", "{}",
                Set.of(), List.of(), Set.of("TEXT"), false, new BigDecimal("0.01000000"),
                new BigDecimal("0.02000000"), "USD", 1000, 30000);
    }

    private RuntimeModelInvocationCommand toolCommand() {
        RuntimeToolSpecification specification = new RuntimeToolSpecification(
                "Knowledge.search", "Knowledge_search", "Search", objectSchema());
        return new RuntimeModelInvocationCommand(1, "first", RuntimeProviderType.API, "first-adapter-v1",
                11, "first-model", 21, "default", "https://example.com", "PLATFORM",
                "course-help", "1.0.0", "system", "question", "{}", "output", "{}",
                Set.of("Knowledge.search"), List.of(specification), Set.of("TEXT"), false,
                null, null, null, 1000, 30000);
    }
    private String objectSchema() {
        return "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":true}";
    }

    private boolean allZero(char[] value) {
        for (char character : value) if (character != '\0') return false;
        return true;
    }

    private static final class RecordingTraceStore implements RuntimeExecutionTraceStore {
        private long nextId = 1;
        private final List<Long> createdIds = new ArrayList<>();
        private final List<CreateRuntimeProviderInvocation> commands = new ArrayList<>();
        private final List<RuntimeProviderInvocationStatus> transitions = new ArrayList<>();
        private final List<RuntimeProviderInvocationResult> results = new ArrayList<>();
        private final List<String> eventTypes = new ArrayList<>();
        private final List<String> eventPayloads = new ArrayList<>();
        @Override public long createProviderInvocation(CreateRuntimeProviderInvocation command) {
            long id = nextId++;
            createdIds.add(id);
            commands.add(command);
            return id;
        }
        @Override public void transitionProviderInvocation(long invocationId,
                RuntimeProviderInvocationStatus target, RuntimeProviderInvocationResult result) {
            transitions.add(target);
            results.add(result);
        }
        @Override public long createExecution(CreateRuntimeExecution command) { throw new UnsupportedOperationException(); }
        @Override public long createNode(CreateRuntimeExecutionNode command) { throw new UnsupportedOperationException(); }
        @Override public void transitionExecution(long executionId, RuntimeExecutionStatus target,
                String resultReference, String errorCode, String errorSummary) { throw new UnsupportedOperationException(); }
        @Override public void transitionNode(long nodeId, RuntimeExecutionNodeStatus target,
                String outputReference, String outputDigest, String errorCode, String errorSummary) {
            throw new UnsupportedOperationException();
        }
        @Override public long appendEvent(long executionId, Long nodeId, String eventType, String payloadJson) {
            eventTypes.add(eventType);
            eventPayloads.add(payloadJson);
            return eventTypes.size();
        }
    }
}
