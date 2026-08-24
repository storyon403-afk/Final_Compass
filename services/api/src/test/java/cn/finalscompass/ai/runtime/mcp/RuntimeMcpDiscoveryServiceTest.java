package cn.finalscompass.ai.runtime.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeMcpDiscoveryServiceTest {
    @Test
    void canonicalizesSortsDigestsAndPersistsCurrentSnapshot() {
        CapturingStore firstStore = new CapturingStore();
        RuntimeMcpDiscoveryReport first = service(firstStore, List.of(
                tool("zeta", "{\"properties\":{\"b\":{\"type\":\"number\"},\"a\":{\"type\":\"string\"}},\"type\":\"object\"}"),
                tool("alpha", "{\"type\":\"object\",\"properties\":{}}"))).discover("knowledge-mcp", 7);
        assertEquals(2, first.toolCount());
        assertEquals("alpha", firstStore.snapshot.tools().getFirst().name());
        assertEquals(64, first.schemaDigest().length());
        assertTrue(firstStore.snapshot.tools().get(1).inputSchemaJson().indexOf("properties")
                < firstStore.snapshot.tools().get(1).inputSchemaJson().indexOf("type"));

        CapturingStore secondStore = new CapturingStore();
        RuntimeMcpDiscoveryReport second = service(secondStore, List.of(
                tool("alpha", "{\"properties\":{},\"type\":\"object\"}"),
                tool("zeta", "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"},\"b\":{\"type\":\"number\"}}}")))
                .discover("knowledge-mcp", 7);
        assertEquals(first.schemaDigest(), second.schemaDigest());
        assertEquals(firstStore.snapshot.tools().get(1).schemaDigest(),
                secondStore.snapshot.tools().get(1).schemaDigest());
    }

    @Test
    void rejectsDuplicateToolsAndDoesNotPersistPartialSnapshot() {
        CapturingStore store = new CapturingStore();
        RuntimeMcpDiscoveryService service = service(store,
                List.of(tool("search", "{\"type\":\"object\"}"),
                        tool("search", "{\"type\":\"object\"}")));
        assertThrows(IllegalArgumentException.class, () -> service.discover("knowledge-mcp", 7));
        assertNull(store.snapshot);
    }

    @Test
    void resolvesUserCredentialAndClearsTransportTokenCopy() {
        CapturingStore store = new CapturingStore();
        char[][] observed = {null};
        RuntimeMcpServerDefinition server = server(RuntimeMcpAuthMode.USER_OAUTH);
        RuntimeMcpTransport transport = new RuntimeMcpTransport() {
            @Override public RuntimeMcpTransportType transportType() {
                return RuntimeMcpTransportType.STREAMABLE_HTTP;
            }
            @Override public RuntimeMcpCallResult callTool(RuntimeMcpCallRequest request, char[] accessToken) {
                throw new UnsupportedOperationException();
            }
            @Override public RuntimeMcpDiscoveryResult discoverTools(RuntimeMcpServerDefinition ignored,
                                                                     char[] accessToken) {
                observed[0] = accessToken;
                return result(List.of(tool("search", "{\"type\":\"object\"}")));
            }
        };
        RuntimeMcpCredentialResolver resolver = new RuntimeMcpCredentialResolver() {
            @Override public RuntimeMcpAuthMode authMode() { return RuntimeMcpAuthMode.USER_OAUTH; }
            @Override public RuntimeMcpCredential resolve(RuntimeMcpServerDefinition ignored, long userId) {
                assertEquals(88, userId);
                return new RuntimeMcpCredential("token".toCharArray());
            }
        };
        var service = new RuntimeMcpDiscoveryService(key -> Optional.of(server),
                new RuntimeMcpTransportRegistry(List.of(transport)),
                new RuntimeMcpCredentialResolverRegistry(List.of(resolver)), store, new ObjectMapper());
        service.discover("knowledge-mcp", 88);
        assertNotNull(observed[0]);
        for (char value : observed[0]) assertEquals('\0', value);
    }

    private RuntimeMcpDiscoveryService service(CapturingStore store,
                                               List<RuntimeMcpDiscoveredTool> tools) {
        RuntimeMcpTransport transport = new RuntimeMcpTransport() {
            @Override public RuntimeMcpTransportType transportType() {
                return RuntimeMcpTransportType.STREAMABLE_HTTP;
            }
            @Override public RuntimeMcpCallResult callTool(RuntimeMcpCallRequest request, char[] accessToken) {
                throw new UnsupportedOperationException();
            }
            @Override public RuntimeMcpDiscoveryResult discoverTools(RuntimeMcpServerDefinition server,
                                                                     char[] accessToken) {
                return result(tools);
            }
        };
        return new RuntimeMcpDiscoveryService(key -> Optional.of(server(RuntimeMcpAuthMode.NONE)),
                new RuntimeMcpTransportRegistry(List.of(transport)),
                new RuntimeMcpCredentialResolverRegistry(List.of()), store, new ObjectMapper());
    }
    private RuntimeMcpDiscoveryResult result(List<RuntimeMcpDiscoveredTool> tools) {
        return new RuntimeMcpDiscoveryResult("2025-06-18", "{\"tools\":{},\"logging\":{}}", tools);
    }
    private RuntimeMcpDiscoveredTool tool(String name, String input) {
        return new RuntimeMcpDiscoveredTool(name, name, "Description", input,
                "{\"type\":\"object\"}", "{}");
    }
    private RuntimeMcpServerDefinition server(RuntimeMcpAuthMode mode) {
        return new RuntimeMcpServerDefinition(1, "knowledge-mcp", "Knowledge MCP",
                RuntimeMcpTransportType.STREAMABLE_HTTP, "https://mcp.example.com/mcp", "2025-06-18",
                mode, mode == RuntimeMcpAuthMode.NONE ? null : "vault:user", RuntimeMcpHealthStatus.HEALTHY,
                "{\"allowedHosts\":[\"mcp.example.com\"]}", "{}");
    }
    private static final class CapturingStore implements RuntimeMcpDiscoveryStore {
        private RuntimeMcpDiscoverySnapshot snapshot;
        @Override public RuntimeMcpDiscoveryPersistResult saveCurrent(RuntimeMcpDiscoverySnapshot snapshot) {
            this.snapshot = snapshot;
            return new RuntimeMcpDiscoveryPersistResult(42, 2);
        }
    }
}
