package cn.finalscompass.ai.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public final class StdioRuntimeMcpTransport implements RuntimeMcpTransport {
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final Set<Path> executables;
  private final List<Path> roots;

  public StdioRuntimeMcpTransport(
      JdbcClient jdbc,
      ObjectMapper json,
      @Value("${app.ai.mcp.stdio-allowed-executables:}") String executables,
      @Value("${app.ai.mcp.stdio-allowed-roots:}") String roots) {
    this.jdbc = jdbc;
    this.json = json;
    this.executables = parsePaths(executables);
    this.roots = new ArrayList<>(parsePaths(roots));
  }

  @Override
  public RuntimeMcpTransportType transportType() {
    return RuntimeMcpTransportType.STDIO;
  }

  @Override
  public RuntimeMcpCallResult callTool(RuntimeMcpCallRequest request, char[] token) {
    try (Session session = open(request.server(), token)) {
      JsonNode args = json.readTree(request.argumentsJson());
      if (!args.isObject()) throw new IllegalArgumentException("MCP arguments invalid");
      JsonNode result =
          session.request(
              "tools/call", Map.of("name", request.remoteToolName(), "arguments", args));
      JsonNode structured = result.path("structuredContent");
      if (!structured.isObject())
        structured = json.createObjectNode().set("content", result.path("content"));
      return new RuntimeMcpCallResult(
          result.path("isError").asBoolean(false), json.writeValueAsString(structured));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeMcpProtocolException("MCP_STDIO_CALL_FAILED", false, e);
    }
  }

  @Override
  public RuntimeMcpDiscoveryResult discoverTools(RuntimeMcpServerDefinition server, char[] token) {
    try (Session session = open(server, token)) {
      List<RuntimeMcpDiscoveredTool> tools = new ArrayList<>();
      Set<String> cursors = new HashSet<>();
      String cursor = null;
      for (int page = 0; page < 20; page++) {
        JsonNode result =
            session.request("tools/list", cursor == null ? Map.of() : Map.of("cursor", cursor));
        if (!result.path("tools").isArray())
          throw new IllegalStateException("MCP tools/list invalid");
        for (JsonNode tool : result.path("tools")) {
          if (tools.size() >= 500 || !tool.path("inputSchema").isObject())
            throw new IllegalStateException("MCP tool list limit");
          tools.add(
              new RuntimeMcpDiscoveredTool(
                  tool.path("name").asText(),
                  text(tool, "title"),
                  text(tool, "description"),
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
              server.protocolVersion(), session.capabilities.toString(), tools);
        if (!cursors.add(cursor)) throw new IllegalStateException("MCP cursor repeated");
      }
      throw new IllegalStateException("MCP page limit");
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeMcpProtocolException("MCP_STDIO_DISCOVERY_FAILED", false, e);
    }
  }

  private Session open(RuntimeMcpServerDefinition server, char[] token) throws Exception {
    if (server.transportType() != RuntimeMcpTransportType.STDIO)
      throw new IllegalArgumentException("Not STDIO MCP");
    Config config =
        jdbc.sql(
                "SELECT stdio_command,stdio_working_directory FROM ai_runtime_mcp_server WHERE"
                    + " id=:id AND status='ACTIVE'")
            .param("id", server.id())
            .query(Config.class)
            .single();
    JsonNode command = json.readTree(config.stdioCommand());
    if (!command.isArray() || command.isEmpty() || command.size() > 32)
      throw new SecurityException("STDIO command invalid");
    List<String> values = new ArrayList<>();
    for (JsonNode item : command) {
      if (!item.isTextual() || item.textValue().length() > 1000)
        throw new SecurityException("STDIO argument invalid");
      values.add(item.textValue());
    }
    Path executable = Path.of(values.getFirst()).toAbsolutePath().normalize();
    if (!executables.contains(executable))
      throw new SecurityException("STDIO executable not allowed");
    ProcessBuilder builder =
        new ProcessBuilder(values).redirectError(ProcessBuilder.Redirect.DISCARD);
    if (config.stdioWorkingDirectory() != null) {
      Path dir = Path.of(config.stdioWorkingDirectory()).toAbsolutePath().normalize();
      if (roots.stream().noneMatch(dir::startsWith))
        throw new SecurityException("STDIO directory not allowed");
      builder.directory(dir.toFile());
    }
    if (token != null && token.length > 0)
      builder.environment().put("MCP_ACCESS_TOKEN", new String(token));
    Process process = builder.start();
    Session session = new Session(process, server.protocolVersion());
    session.initialize();
    return session;
  }

  private Set<Path> parsePaths(String value) {
    Set<Path> result = new HashSet<>();
    if (value != null && !value.isBlank())
      for (String item : value.split(","))
        result.add(Path.of(item.trim()).toAbsolutePath().normalize());
    return Set.copyOf(result);
  }

  private String text(JsonNode node, String key) {
    return node.path(key).isTextual() ? node.path(key).textValue() : null;
  }

  private record Config(String stdioCommand, String stdioWorkingDirectory) {}

  private final class Session implements AutoCloseable {
    final Process process;
    final BufferedWriter writer;
    final BufferedReader reader;
    final String protocol;
    JsonNode capabilities;

    Session(Process process, String protocol) {
      this.process = process;
      this.protocol = protocol;
      writer =
          new BufferedWriter(
              new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
      reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    void initialize() throws Exception {
      JsonNode result =
          request(
              "initialize",
              Map.of(
                  "protocolVersion",
                  protocol,
                  "capabilities",
                  Map.of(),
                  "clientInfo",
                  Map.of("name", "finals-compass-runtime", "version", "0.1.0")));
      if (!protocol.equals(result.path("protocolVersion").asText())
          || !result.path("capabilities").path("tools").isObject())
        throw new IllegalStateException("MCP negotiation failed");
      capabilities = result.path("capabilities");
      send(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
    }

    JsonNode request(String method, Map<String, Object> params) throws Exception {
      String id = UUID.randomUUID().toString();
      send(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
      for (int i = 0; i < 50; i++) {
        String line =
            CompletableFuture.supplyAsync(
                    () -> {
                      try {
                        return reader.readLine();
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    })
                .get(30, TimeUnit.SECONDS);
        if (line == null || line.length() > 2 * 1024 * 1024)
          throw new IllegalStateException("MCP STDIO response invalid");
        JsonNode message = json.readTree(line);
        if (id.equals(message.path("id").asText())) {
          if (message.path("error").isObject())
            throw new RuntimeMcpProtocolException("MCP_JSON_RPC_ERROR", false, null);
          return message.path("result");
        }
      }
      throw new IllegalStateException("MCP response missing");
    }

    void send(Object message) throws Exception {
      writer.write(json.writeValueAsString(message));
      writer.newLine();
      writer.flush();
    }

    @Override
    public void close() {
      try {
        writer.close();
      } catch (Exception ignored) {
      }
      process.destroy();
      try {
        if (!process.waitFor(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS))
          process.destroyForcibly();
      } catch (Exception e) {
        process.destroyForcibly();
      }
    }
  }
}
