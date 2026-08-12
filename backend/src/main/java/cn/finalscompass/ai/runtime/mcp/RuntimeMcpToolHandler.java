package cn.finalscompass.ai.runtime.mcp;

import cn.finalscompass.ai.runtime.tool.RuntimeToolDefinition;
import cn.finalscompass.ai.runtime.tool.RuntimeToolExecutionContext;
import cn.finalscompass.ai.runtime.tool.RuntimeToolHandler;
import cn.finalscompass.ai.runtime.tool.RuntimeToolTransportType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class RuntimeMcpToolHandler implements RuntimeToolHandler {
  public static final String EXECUTOR_KEY = "mcp-gateway";
  private final RuntimeMcpToolBindingRepository bindings;
  private final RuntimeMcpTransportRegistry transports;
  private final RuntimeMcpCredentialResolverRegistry credentials;
  private final ObjectMapper json;

  public RuntimeMcpToolHandler(
      RuntimeMcpToolBindingRepository bindings,
      RuntimeMcpTransportRegistry transports,
      RuntimeMcpCredentialResolverRegistry credentials,
      ObjectMapper json) {
    this.bindings = bindings;
    this.transports = transports;
    this.credentials = credentials;
    this.json = json;
  }

  @Override
  public String executorKey() {
    return EXECUTOR_KEY;
  }

  @Override
  public String invoke(
      RuntimeToolDefinition definition, RuntimeToolExecutionContext context, String argumentsJson) {
    if (definition.transportType() != RuntimeToolTransportType.MCP
        || !EXECUTOR_KEY.equals(definition.executorKey()))
      throw new IllegalArgumentException("Runtime Tool is not an MCP Gateway Tool");
    RuntimeMcpToolBinding binding =
        bindings
            .findActive(definition.toolKey(), definition.version())
            .orElseThrow(
                () -> new IllegalStateException("Runtime MCP Tool binding is unavailable"));
    RuntimeMcpServerDefinition server = binding.server();
    if (server.healthStatus() != RuntimeMcpHealthStatus.HEALTHY
        && server.healthStatus() != RuntimeMcpHealthStatus.DEGRADED)
      throw new IllegalStateException("Runtime MCP Server is unhealthy");
    try (RuntimeMcpCredential credential = credentials.resolve(server, context.userId())) {
      char[] token = credential.accessToken();
      try {
        RuntimeMcpCallResult result =
            transports
                .require(server.transportType())
                .callTool(
                    new RuntimeMcpCallRequest(
                        server,
                        binding.remoteToolName(),
                        argumentsJson,
                        context.executionId(),
                        context.nodeId(),
                        context.userId()),
                    token);
        if (result == null || result.structuredContentJson() == null)
          throw new IllegalStateException("Runtime MCP Server returned no structured content");
        JsonNode structured = json.readTree(result.structuredContentJson());
        if (!structured.isObject())
          throw new IllegalStateException("Runtime MCP structured content is invalid");
        return result.structuredContentJson();
      } catch (RuntimeException exception) {
        throw exception;
      } catch (Exception exception) {
        throw new IllegalStateException("Runtime MCP Tool call failed", exception);
      } finally {
        if (token != null) java.util.Arrays.fill(token, '\0');
      }
    }
  }
}
