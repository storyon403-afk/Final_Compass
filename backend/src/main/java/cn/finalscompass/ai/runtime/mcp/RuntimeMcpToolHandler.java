package cn.finalscompass.ai.runtime.mcp;

import cn.finalscompass.ai.runtime.tool.RuntimeToolDefinition;
import cn.finalscompass.ai.runtime.tool.RuntimeToolExecutionContext;
import cn.finalscompass.ai.runtime.tool.RuntimeToolHandler;
import cn.finalscompass.ai.runtime.tool.RuntimeToolTransportType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 把统一运行时工具调用转发到 MCP 服务器，并校验绑定、凭据和返回结果
 * 维护入口：调用前治理规则改这里；网络协议细节改 RuntimeMcpTransport 实现
 */
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

  /**
   * 调用外部服务并解析返回结果
   * 实现上，通过 Jackson 完成 JSON 的解析或序列化；在结束时主动释放资源或擦除敏感数据
   *
   * @param definition 待保存或校验的定义
   * @param context 本次工具调用的运行上下文
   * @param argumentsJson 工具参数 JSON
   * @return 处理后的业务结果
   */
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
