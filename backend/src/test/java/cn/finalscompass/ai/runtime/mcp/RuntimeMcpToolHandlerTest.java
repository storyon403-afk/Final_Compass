package cn.finalscompass.ai.runtime.mcp;

import cn.finalscompass.ai.runtime.tool.RuntimeToolDefinition;
import cn.finalscompass.ai.runtime.tool.RuntimeToolExecutionContext;
import cn.finalscompass.ai.runtime.tool.RuntimeToolTransportType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeMcpToolHandlerTest {
    @Test
    void resolvesBindingCredentialAndTransportWithoutExposingTokenInRequest() {
        RuntimeMcpServerDefinition server = server(RuntimeMcpHealthStatus.HEALTHY,
                RuntimeMcpAuthMode.USER_OAUTH);
        RuntimeMcpToolBindingRepository bindings = (key, version) -> Optional.of(
                new RuntimeMcpToolBinding(key, version, server, "search", "a".repeat(64)));
        char[][] observedToken = {null};
        RuntimeMcpTransport transport = new RuntimeMcpTransport() {
            @Override public RuntimeMcpTransportType transportType() {
                return RuntimeMcpTransportType.STREAMABLE_HTTP;
            }
            @Override public RuntimeMcpCallResult callTool(RuntimeMcpCallRequest request, char[] accessToken) {
                assertEquals("search", request.remoteToolName());
                assertEquals(77, request.userId());
                observedToken[0] = accessToken;
                return new RuntimeMcpCallResult(false, "{\"matches\":[]}");
            }
        };
        RuntimeMcpCredentialResolver resolver = new RuntimeMcpCredentialResolver() {
            @Override public RuntimeMcpAuthMode authMode() { return RuntimeMcpAuthMode.USER_OAUTH; }
            @Override public RuntimeMcpCredential resolve(RuntimeMcpServerDefinition ignored, long userId) {
                return new RuntimeMcpCredential("short-lived-token".toCharArray());
            }
        };
        RuntimeMcpToolHandler handler = new RuntimeMcpToolHandler(bindings,
                new RuntimeMcpTransportRegistry(List.of(transport)),
                new RuntimeMcpCredentialResolverRegistry(List.of(resolver)), new ObjectMapper());
        String result = handler.invoke(tool(), context(), "{\"query\":\"calculus\"}");
        assertEquals("{\"matches\":[]}", result);
        assertNotNull(observedToken[0]);
        assertTrue(allZero(observedToken[0]));
    }

    @Test
    void failsClosedForUnhealthyServerMissingTransportOrCredential() {
        RuntimeMcpToolBindingRepository unhealthy = (key, version) -> Optional.of(
                new RuntimeMcpToolBinding(key, version,
                        server(RuntimeMcpHealthStatus.UNHEALTHY, RuntimeMcpAuthMode.NONE),
                        "search", "a".repeat(64)));
        RuntimeMcpToolHandler unhealthyHandler = new RuntimeMcpToolHandler(unhealthy,
                new RuntimeMcpTransportRegistry(List.of()),
                new RuntimeMcpCredentialResolverRegistry(List.of()), new ObjectMapper());
        assertThrows(IllegalStateException.class,
                () -> unhealthyHandler.invoke(tool(), context(), "{\"query\":\"x\"}"));

        RuntimeMcpToolBindingRepository healthy = (key, version) -> Optional.of(
                new RuntimeMcpToolBinding(key, version,
                        server(RuntimeMcpHealthStatus.HEALTHY, RuntimeMcpAuthMode.USER_OAUTH),
                        "search", "a".repeat(64)));
        RuntimeMcpToolHandler missingCredentialHandler = new RuntimeMcpToolHandler(
                healthy, new RuntimeMcpTransportRegistry(List.of()),
                new RuntimeMcpCredentialResolverRegistry(List.of()), new ObjectMapper());
        assertThrows(IllegalStateException.class,
                () -> missingCredentialHandler.invoke(tool(), context(), "{\"query\":\"x\"}"));
    }

    @Test
    void registryRejectsDuplicateTransportImplementations() {
        RuntimeMcpTransport transport = new RuntimeMcpTransport() {
            @Override public RuntimeMcpTransportType transportType() { return RuntimeMcpTransportType.STDIO; }
            @Override public RuntimeMcpCallResult callTool(RuntimeMcpCallRequest request, char[] accessToken) {
                return new RuntimeMcpCallResult(false, "{}");
            }
        };
        assertThrows(IllegalStateException.class,
                () -> new RuntimeMcpTransportRegistry(List.of(transport, transport)));
    }

    private RuntimeMcpServerDefinition server(RuntimeMcpHealthStatus health, RuntimeMcpAuthMode auth) {
        return new RuntimeMcpServerDefinition(1, "knowledge-mcp", "Knowledge MCP",
                RuntimeMcpTransportType.STREAMABLE_HTTP, "https://mcp.example.com/mcp", "2025-06-18",
                auth, auth == RuntimeMcpAuthMode.NONE ? null : "vault:mcp/user", health, "{}", "{}");
    }
    private RuntimeToolDefinition tool() {
        return new RuntimeToolDefinition(1, "Knowledge.search", "Search", "Search knowledge", "1.0.0",
                RuntimeToolTransportType.MCP, RuntimeMcpToolHandler.EXECUTOR_KEY,
                "{\"type\":\"object\"}", "{\"type\":\"object\"}",
                Set.of("KNOWLEDGE_READ"), "{}", 5000, 1024);
    }
    private RuntimeToolExecutionContext context() {
        return new RuntimeToolExecutionContext(10, 20, 77, "course-help",
                Set.of("Knowledge.search"), Set.of("KNOWLEDGE_READ"));
    }
    private boolean allZero(char[] value) {
        for (char character : value) if (character != '\0') return false;
        return true;
    }
}
