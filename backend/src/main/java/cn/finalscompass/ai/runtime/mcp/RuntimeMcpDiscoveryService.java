package cn.finalscompass.ai.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/*
 * 维护流程图：
 *   ServerDefinition --> CredentialResolver --> Transport.discoverTools
 *          --> normalize --> 计算摘要 --> DiscoveryStore 快照/绑定 --> Report
 */
/**
 * 连接 MCP 服务器发现工具，完成规范化、摘要比对和快照持久化
 * 维护入口：发现协议改 Transport；工具规范化改 normalize；快照策略改 DiscoveryStore
 */
@Service
public final class RuntimeMcpDiscoveryService {
  private final RuntimeMcpServerRepository servers;
  private final RuntimeMcpTransportRegistry transports;
  private final RuntimeMcpCredentialResolverRegistry credentials;
  private final RuntimeMcpDiscoveryStore snapshots;
  private final ObjectMapper json;

  public RuntimeMcpDiscoveryService(
      RuntimeMcpServerRepository servers,
      RuntimeMcpTransportRegistry transports,
      RuntimeMcpCredentialResolverRegistry credentials,
      RuntimeMcpDiscoveryStore snapshots,
      ObjectMapper json) {
    this.servers = servers;
    this.transports = transports;
    this.credentials = credentials;
    this.snapshots = snapshots;
    this.json = json;
  }

  // 发现并同步 MCP 服务器提供的工具。在结束时主动释放资源或擦除敏感数据
  public RuntimeMcpDiscoveryReport discover(String serverKey, long requestedByUserId) {
    if (requestedByUserId <= 0) throw new IllegalArgumentException("MCP discovery user is invalid");
    RuntimeMcpServerDefinition server =
        servers
            .findActiveByKey(serverKey)
            .orElseThrow(() -> new IllegalStateException("Runtime MCP Server is unavailable"));
    RuntimeMcpDiscoveryResult discovered;
    try (RuntimeMcpCredential credential = credentials.resolve(server, requestedByUserId)) {
      char[] token = credential.accessToken();
      try {
        discovered = transports.require(server.transportType()).discoverTools(server, token);
      } finally {
        if (token != null) java.util.Arrays.fill(token, '\0');
      }
    }
    RuntimeMcpDiscoverySnapshot snapshot = normalize(server, discovered);
    RuntimeMcpDiscoveryPersistResult persisted = snapshots.saveCurrent(snapshot);
    return new RuntimeMcpDiscoveryReport(
        snapshot.discoveryId(),
        persisted.snapshotId(),
        server.serverKey(),
        snapshot.schemaDigest(),
        snapshot.tools().size(),
        persisted.staleBindings());
  }

  // 把供应商数据转换为内部统一格式。利用流式过滤和排序得到符合约束的稳定结果
  private RuntimeMcpDiscoverySnapshot normalize(
      RuntimeMcpServerDefinition server, RuntimeMcpDiscoveryResult result) {
    if (result == null
        || !server.protocolVersion().equals(result.protocolVersion())
        || result.tools().size() > 500)
      throw new IllegalArgumentException("Runtime MCP discovery result is invalid");
    String capabilities = canonicalObject(result.serverCapabilitiesJson(), "MCP capabilities");
    Set<String> names = new HashSet<>();
    List<RuntimeMcpNormalizedTool> tools = new ArrayList<>();
    for (RuntimeMcpDiscoveredTool source : result.tools()) {
      if (source.name() == null
          || !source.name().matches("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,159}")
          || !names.add(source.name()))
        throw new IllegalArgumentException(
            "Runtime MCP discovered Tool name is invalid or duplicated");
      String input = canonicalObject(source.inputSchemaJson(), "MCP input schema");
      String output =
          source.outputSchemaJson() == null
              ? null
              : canonicalObject(source.outputSchemaJson(), "MCP output schema");
      String annotations = canonicalObject(source.annotationsJson(), "MCP annotations");
      String digest = digest(input + "\n" + (output == null ? "null" : output));
      tools.add(
          new RuntimeMcpNormalizedTool(
              source.name(),
              limited(source.title(), 200),
              limited(source.description(), 2000, ""),
              input,
              output,
              annotations,
              digest));
    }
    tools.sort(Comparator.comparing(RuntimeMcpNormalizedTool::name));
    StringBuilder manifest =
        new StringBuilder(result.protocolVersion()).append('\n').append(capabilities);
    tools.forEach(
        tool -> manifest.append('\n').append(tool.name()).append(':').append(tool.schemaDigest()));
    return new RuntimeMcpDiscoverySnapshot(
        UUID.randomUUID().toString(),
        server.id(),
        result.protocolVersion(),
        capabilities,
        digest(manifest.toString()),
        tools);
  }

  // 递归规范化 JSON 对象的字段顺序。通过 Jackson 完成 JSON 的解析或序列化
  private String canonicalObject(String value, String label) {
    try {
      JsonNode node = json.readTree(value);
      if (node == null || !node.isObject()) throw new IllegalArgumentException();
      return json.writeValueAsString(canonical(node));
    } catch (Exception exception) {
      throw new IllegalArgumentException(label + " is invalid", exception);
    }
  }

  // 把输入转换为稳定的规范字符串。通过 Jackson 完成 JSON 的解析或序列化；利用流式过滤和排序得到符合约束的稳定结果
  private JsonNode canonical(JsonNode node) {
    if (node.isObject()) {
      ObjectNode result = json.createObjectNode();
      node.properties().stream()
          .sorted(java.util.Map.Entry.comparingByKey())
          .forEach(entry -> result.set(entry.getKey(), canonical(entry.getValue())));
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = json.createArrayNode();
      node.forEach(value -> result.add(canonical(value)));
      return result;
    }
    return node.deepCopy();
  }

  // 计算工具定义的稳定摘要。通过摘要或 Base64 编码生成稳定且可传输的标识
  private String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private String limited(String value, int max) {
    return limited(value, max, null);
  }

  // 截断过长的外部文本。主路径不可用时按候选顺序尝试备用项，提高调用成功率
  private String limited(String value, int max, String fallback) {
    String normalized = value == null ? fallback : value;
    if (normalized != null && normalized.length() > max)
      throw new IllegalArgumentException("Runtime MCP discovered Tool metadata is too long");
    return normalized;
  }
}
