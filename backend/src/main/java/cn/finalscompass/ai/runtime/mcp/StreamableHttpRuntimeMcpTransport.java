package cn.finalscompass.ai.runtime.mcp;

import cn.finalscompass.ai.runtime.provider.client.RuntimeHttpRequest;
import cn.finalscompass.ai.runtime.provider.client.RuntimeHttpResponse;
import cn.finalscompass.ai.runtime.provider.client.RuntimeHttpTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class StreamableHttpRuntimeMcpTransport implements RuntimeMcpTransport {
  private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
  private final RuntimeHttpTransport http;
  private final ObjectMapper json;

  public StreamableHttpRuntimeMcpTransport(RuntimeHttpTransport http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  @Override
  public RuntimeMcpTransportType transportType() {
    return RuntimeMcpTransportType.STREAMABLE_HTTP;
  }

  @Override
  public RuntimeMcpCallResult callTool(RuntimeMcpCallRequest request, char[] accessToken) {
    if (request == null
        || request.server() == null
        || request.executionId() <= 0
        || request.nodeId() <= 0
        || request.userId() <= 0
        || !validToolName(request.remoteToolName()))
      throw new IllegalArgumentException("Runtime MCP call request is invalid");
    try {
      JsonNode arguments = json.readTree(request.argumentsJson());
      if (!arguments.isObject())
        throw new IllegalArgumentException("Runtime MCP arguments must be an object");
      Session session = initialize(request.server(), accessToken);
      String id = UUID.randomUUID().toString();
      JsonNode response =
          request(
              session,
              accessToken,
              id,
              "tools/call",
              Map.of("name", request.remoteToolName(), "arguments", arguments));
      JsonNode result = response.path("result");
      if (!result.isObject()) throw failure("MCP_TOOL_RESULT_INVALID", false, null);
      JsonNode structured = result.path("structuredContent");
      JsonNode normalized =
          structured.isObject()
              ? structured
              : json.createObjectNode()
                  .set(
                      "content",
                      result.path("content").isArray()
                          ? result.path("content")
                          : json.createArrayNode());
      if (!structured.isObject())
        ((com.fasterxml.jackson.databind.node.ObjectNode) normalized)
            .put("isError", result.path("isError").asBoolean(false));
      return new RuntimeMcpCallResult(
          result.path("isError").asBoolean(false), json.writeValueAsString(normalized));
    } catch (RuntimeMcpProtocolException | IllegalArgumentException | SecurityException exception) {
      throw exception;
    } catch (Exception exception) {
      throw failure("MCP_TOOL_CALL_FAILED", timeout(exception), exception);
    }
  }

  @Override
  public RuntimeMcpDiscoveryResult discoverTools(
      RuntimeMcpServerDefinition server, char[] accessToken) {
    try {
      Session session = initialize(server, accessToken);
      List<RuntimeMcpDiscoveredTool> tools = new ArrayList<>();
      Set<String> cursors = new HashSet<>();
      String cursor = null;
      for (int page = 0; page < 20; page++) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> params = cursor == null ? Map.of() : Map.of("cursor", cursor);
        JsonNode result = request(session, accessToken, id, "tools/list", params).path("result");
        if (!result.path("tools").isArray()) throw failure("MCP_TOOL_LIST_INVALID", false, null);
        for (JsonNode tool : result.path("tools")) {
          if (tools.size() >= 500
              || !validToolName(tool.path("name").asText())
              || !tool.path("inputSchema").isObject())
            throw failure("MCP_TOOL_LIST_INVALID", false, null);
          tools.add(
              new RuntimeMcpDiscoveredTool(
                  tool.path("name").asText(),
                  text(tool, "title", 200),
                  text(tool, "description", 2000),
                  json.writeValueAsString(tool.path("inputSchema")),
                  tool.path("outputSchema").isObject()
                      ? json.writeValueAsString(tool.path("outputSchema"))
                      : null,
                  tool.path("annotations").isObject()
                      ? json.writeValueAsString(tool.path("annotations"))
                      : "{}"));
        }
        cursor = result.path("nextCursor").asText("");
        if (cursor.isBlank())
          return new RuntimeMcpDiscoveryResult(
              session.protocolVersion(), json.writeValueAsString(session.capabilities()), tools);
        if (cursor.length() > 1000 || !cursors.add(cursor))
          throw failure("MCP_TOOL_LIST_CURSOR_INVALID", false, null);
      }
      throw failure("MCP_TOOL_LIST_PAGE_LIMIT", false, null);
    } catch (RuntimeMcpProtocolException | IllegalArgumentException | SecurityException exception) {
      throw exception;
    } catch (Exception exception) {
      throw failure("MCP_TOOL_DISCOVERY_FAILED", timeout(exception), exception);
    }
  }

  private Session initialize(RuntimeMcpServerDefinition server, char[] accessToken)
      throws Exception {
    validateServer(server);
    String id = UUID.randomUUID().toString();
    Map<String, Object> body =
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            id,
            "method",
            "initialize",
            "params",
            Map.of(
                "protocolVersion",
                server.protocolVersion(),
                "capabilities",
                Map.of(),
                "clientInfo",
                Map.of("name", "finals-compass-runtime", "version", "0.1.0")));
    RuntimeHttpResponse raw = post(server, accessToken, null, json.writeValueAsString(body));
    JsonNode response = response(raw, id);
    JsonNode result = response.path("result");
    String negotiated = result.path("protocolVersion").asText("");
    if (!server.protocolVersion().equals(negotiated)
        || !result.path("capabilities").path("tools").isObject())
      throw failure("MCP_CAPABILITY_NEGOTIATION_FAILED", false, null);
    String sessionId = header(raw.headers(), "mcp-session-id");
    if (sessionId != null
        && (!sessionId.matches("^[\\x21-\\x7E]{1,512}$") || sessionId.indexOf(' ') >= 0))
      throw failure("MCP_SESSION_ID_INVALID", false, null);
    Map<String, Object> notification =
        Map.of("jsonrpc", "2.0", "method", "notifications/initialized");
    RuntimeHttpResponse accepted =
        post(server, accessToken, sessionId, json.writeValueAsString(notification));
    if (accepted.statusCode() != 202)
      throw failure("MCP_INITIALIZED_REJECTED", retryable(accepted.statusCode()), null);
    return new Session(server, negotiated, result.path("capabilities"), sessionId);
  }

  private JsonNode request(
      Session session, char[] token, String id, String method, Map<String, Object> params)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("jsonrpc", "2.0");
    body.put("id", id);
    body.put("method", method);
    body.put("params", params);
    return response(
        post(session.server(), token, session.sessionId(), json.writeValueAsString(body)), id);
  }

  private RuntimeHttpResponse post(
      RuntimeMcpServerDefinition server, char[] token, String sessionId, String body) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json, text/event-stream");
    headers.put("MCP-Protocol-Version", server.protocolVersion());
    if (sessionId != null) headers.put("Mcp-Session-Id", sessionId);
    if (token != null && token.length > 0)
      headers.put("Authorization", "Bearer " + new String(token));
    return http.postJson(
        new RuntimeHttpRequest(
            URI.create(server.endpointUri()),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            headers,
            body,
            MAX_RESPONSE_BYTES));
  }

  private JsonNode response(RuntimeHttpResponse raw, String expectedId) throws Exception {
    if (raw.statusCode() / 100 != 2)
      throw failure("MCP_HTTP_" + raw.statusCode(), retryable(raw.statusCode()), null);
    String contentType = header(raw.headers(), "content-type");
    List<JsonNode> messages =
        contentType != null && contentType.toLowerCase().startsWith("text/event-stream")
            ? sseMessages(raw.body())
            : List.of(json.readTree(raw.body()));
    for (JsonNode message : messages)
      if (expectedId.equals(message.path("id").asText())) {
        if (message.path("error").isObject()) throw failure("MCP_JSON_RPC_ERROR", false, null);
        if (!"2.0".equals(message.path("jsonrpc").asText()) || !message.path("result").isObject())
          throw failure("MCP_JSON_RPC_RESPONSE_INVALID", false, null);
        return message;
      }
    throw failure("MCP_JSON_RPC_RESPONSE_MISSING", true, null);
  }

  private List<JsonNode> sseMessages(String body) throws Exception {
    List<JsonNode> result = new ArrayList<>();
    StringBuilder data = new StringBuilder();
    for (String line : body.split("\\r?\\n", -1)) {
      if (line.isEmpty()) {
        if (!data.isEmpty()) {
          result.add(json.readTree(data.toString()));
          data.setLength(0);
        }
      } else if (line.startsWith("data:")) {
        if (!data.isEmpty()) data.append('\n');
        data.append(line.substring(5).stripLeading());
      }
    }
    if (!data.isEmpty()) result.add(json.readTree(data.toString()));
    return result;
  }

  private void validateServer(RuntimeMcpServerDefinition server) {
    if (server == null
        || server.transportType() != RuntimeMcpTransportType.STREAMABLE_HTTP
        || !server.protocolVersion().matches("20[0-9]{2}-[0-9]{2}-[0-9]{2}"))
      throw new IllegalArgumentException("Runtime MCP Server is invalid");
    URI uri = URI.create(server.endpointUri());
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null)
      throw new IllegalArgumentException("Runtime MCP endpoint is invalid");
    try {
      JsonNode policy = json.readTree(server.outboundPolicyJson());
      JsonNode hosts = policy.path("allowedHosts");
      if (!hosts.isArray()
          || !java.util.stream.StreamSupport.stream(hosts.spliterator(), false)
              .anyMatch(
                  host -> host.isTextual() && uri.getHost().equalsIgnoreCase(host.textValue())))
        throw new SecurityException("Runtime MCP endpoint is outside outbound policy");
    } catch (SecurityException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Runtime MCP outbound policy is invalid");
    }
  }

  private String text(JsonNode node, String field, int max) {
    String value = node.path(field).isTextual() ? node.path(field).textValue() : null;
    if (value != null && value.length() > max) throw failure("MCP_TOOL_LIST_INVALID", false, null);
    return value;
  }

  private String header(Map<String, List<String>> headers, String name) {
    return headers.entrySet().stream()
        .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
        .flatMap(entry -> entry.getValue().stream())
        .findFirst()
        .orElse(null);
  }

  private boolean validToolName(String value) {
    return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,159}");
  }

  private boolean retryable(int status) {
    return status == 429 || status >= 500;
  }

  private boolean timeout(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause())
      if (current.getClass().getSimpleName().toLowerCase().contains("timeout")) return true;
    return false;
  }

  private RuntimeMcpProtocolException failure(String code, boolean retryable, Throwable cause) {
    return new RuntimeMcpProtocolException(code, retryable, cause);
  }

  private record Session(
      RuntimeMcpServerDefinition server,
      String protocolVersion,
      JsonNode capabilities,
      String sessionId) {}
}
