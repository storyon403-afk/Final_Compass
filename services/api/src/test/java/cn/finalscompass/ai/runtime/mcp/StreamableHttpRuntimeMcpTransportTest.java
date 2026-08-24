package cn.finalscompass.ai.runtime.mcp;

import cn.finalscompass.ai.runtime.provider.client.RuntimeHttpRequest;
import cn.finalscompass.ai.runtime.provider.client.RuntimeHttpResponse;
import cn.finalscompass.ai.runtime.provider.client.RuntimeHttpTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StreamableHttpRuntimeMcpTransportTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void initializesSessionCallsToolAndNormalizesStructuredResult() throws Exception {
        QueueHttp http = new QueueHttp(initialize("request-session"), accepted(),
                jsonResponse("{\"structuredContent\":{\"matches\":[\"chapter-1\"]},\"isError\":false}"));
        var transport = new StreamableHttpRuntimeMcpTransport(http, json);
        char[] token = "short-lived-token".toCharArray();
        RuntimeMcpCallResult result = transport.callTool(new RuntimeMcpCallRequest(
                server(), "search", "{\"query\":\"calculus\"}", 1, 2, 3), token);
        assertFalse(result.error());
        assertEquals("chapter-1", json.readTree(result.structuredContentJson()).path("matches").get(0).asText());
        assertEquals(3, http.requests.size());
        assertEquals("request-session", http.requests.get(1).headers().get("Mcp-Session-Id"));
        assertEquals("request-session", http.requests.get(2).headers().get("Mcp-Session-Id"));
        assertEquals("2025-06-18", http.requests.get(2).headers().get("MCP-Protocol-Version"));
        assertTrue(http.requests.get(2).headers().get("Accept").contains("text/event-stream"));
        assertFalse(http.requests.stream().anyMatch(request -> request.body().contains("short-lived-token")));
        JsonNode call = json.readTree(http.requests.get(2).body());
        assertEquals("tools/call", call.path("method").asText());
        assertEquals("search", call.path("params").path("name").asText());
    }

    @Test
    void supportsSseResponseAndPaginatedToolDiscovery() {
        QueueHttp callHttp = new QueueHttp(initialize(null), accepted(), new RuntimeHttpResponse(200,
                Map.of("content-type", List.of("text/event-stream")),
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":\"ignored\",\"method\":\"ping\"}\n\n"
                        + "data: {\"jsonrpc\":\"2.0\",\"id\":\"DYNAMIC\",\"result\":{"
                        + "\"structuredContent\":{\"ok\":true}}}\n\n"));
        callHttp.replaceDynamicId = true;
        RuntimeMcpCallResult callResult = new StreamableHttpRuntimeMcpTransport(callHttp, json).callTool(
                new RuntimeMcpCallRequest(server(), "search", "{}", 1, 2, 3), null);
        assertTrue(read(callResult.structuredContentJson()).path("ok").asBoolean());

        QueueHttp discoveryHttp = new QueueHttp(initialize("s1"), accepted(),
                jsonResponse("{\"tools\":[{\"name\":\"search\",\"description\":\"Search\","
                        + "\"inputSchema\":{\"type\":\"object\"}}],\"nextCursor\":\"page-2\"}"),
                jsonResponse("{\"tools\":[{\"name\":\"fetch\",\"description\":\"Fetch\","
                        + "\"inputSchema\":{\"type\":\"object\"},"
                        + "\"outputSchema\":{\"type\":\"object\"}}]}"));
        RuntimeMcpDiscoveryResult discovery = new StreamableHttpRuntimeMcpTransport(discoveryHttp, json)
                .discoverTools(server(), null);
        assertEquals(2, discovery.tools().size());
        assertEquals("fetch", discovery.tools().get(1).name());
        assertTrue(discoveryHttp.requests.get(3).body().contains("page-2"));
    }

    @Test
    void rejectsUnapprovedHostProtocolErrorsAndRepeatedCursor() {
        RuntimeMcpServerDefinition unapproved = new RuntimeMcpServerDefinition(1, "mcp", "MCP",
                RuntimeMcpTransportType.STREAMABLE_HTTP, "https://evil.example/mcp", "2025-06-18",
                RuntimeMcpAuthMode.NONE, null, RuntimeMcpHealthStatus.HEALTHY,
                "{\"allowedHosts\":[\"mcp.example.com\"]}", "{}");
        var transport = new StreamableHttpRuntimeMcpTransport(new QueueHttp(), json);
        assertThrows(SecurityException.class, () -> transport.discoverTools(unapproved, null));

        QueueHttp rpcError = new QueueHttp(initialize(null), accepted(),
                new RuntimeHttpResponse(200, Map.of("content-type", List.of("application/json")),
                        "{\"jsonrpc\":\"2.0\",\"id\":\"DYNAMIC\",\"error\":{\"code\":-32601}}"));
        rpcError.replaceDynamicId = true;
        RuntimeMcpProtocolException exception = assertThrows(RuntimeMcpProtocolException.class,
                () -> new StreamableHttpRuntimeMcpTransport(rpcError, json).callTool(
                        new RuntimeMcpCallRequest(server(), "search", "{}", 1, 2, 3), null));
        assertEquals("MCP_JSON_RPC_ERROR", exception.errorCode());

        QueueHttp cursor = new QueueHttp(initialize(null), accepted(),
                jsonResponse("{\"tools\":[],\"nextCursor\":\"same\"}"),
                jsonResponse("{\"tools\":[],\"nextCursor\":\"same\"}"));
        assertThrows(RuntimeMcpProtocolException.class,
                () -> new StreamableHttpRuntimeMcpTransport(cursor, json).discoverTools(server(), null));
    }

    private RuntimeMcpServerDefinition server() {
        return new RuntimeMcpServerDefinition(1, "knowledge-mcp", "Knowledge MCP",
                RuntimeMcpTransportType.STREAMABLE_HTTP, "https://mcp.example.com/mcp", "2025-06-18",
                RuntimeMcpAuthMode.NONE, null, RuntimeMcpHealthStatus.HEALTHY,
                "{\"allowedHosts\":[\"mcp.example.com\"]}", "{}");
    }
    private RuntimeHttpResponse initialize(String sessionId) {
        Map<String, List<String>> headers = sessionId == null
                ? Map.of("content-type", List.of("application/json"))
                : Map.of("content-type", List.of("application/json"), "Mcp-Session-Id", List.of(sessionId));
        return new RuntimeHttpResponse(200, headers, "{\"jsonrpc\":\"2.0\",\"id\":\"DYNAMIC\","
                + "\"result\":{\"protocolVersion\":\"2025-06-18\","
                + "\"capabilities\":{\"tools\":{}}}}" );
    }
    private RuntimeHttpResponse accepted() { return new RuntimeHttpResponse(202, Map.of(), ""); }
    private RuntimeHttpResponse jsonResponse(String result) {
        return new RuntimeHttpResponse(200, Map.of("content-type", List.of("application/json")),
                "{\"jsonrpc\":\"2.0\",\"id\":\"DYNAMIC\",\"result\":" + result + "}");
    }
    private JsonNode read(String value) {
        try { return json.readTree(value); } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static final class QueueHttp implements RuntimeHttpTransport {
        private final ArrayDeque<RuntimeHttpResponse> responses;
        private final List<RuntimeHttpRequest> requests = new ArrayList<>();
        private boolean replaceDynamicId = true;
        private QueueHttp(RuntimeHttpResponse... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }
        @Override public RuntimeHttpResponse postJson(RuntimeHttpRequest request) {
            requests.add(request);
            RuntimeHttpResponse response = responses.removeFirst();
            if (!replaceDynamicId || !response.body().contains("DYNAMIC")) return response;
            try {
                String id = new ObjectMapper().readTree(request.body()).path("id").asText();
                return new RuntimeHttpResponse(response.statusCode(), response.headers(),
                        response.body().replace("DYNAMIC", id));
            } catch (Exception exception) { throw new AssertionError(exception); }
        }
    }
}
