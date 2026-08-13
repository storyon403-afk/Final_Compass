package cn.finalscompass.ai.runtime.mcp;

import java.util.EnumMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 集中注册和查找运行时MCP凭据解析器实现，避免调用方直接依赖具体类。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
@Component
public final class RuntimeMcpCredentialResolverRegistry {
  private final EnumMap<RuntimeMcpAuthMode, RuntimeMcpCredentialResolver> resolvers =
      new EnumMap<>(RuntimeMcpAuthMode.class);

  public RuntimeMcpCredentialResolverRegistry(List<RuntimeMcpCredentialResolver> values) {
    for (RuntimeMcpCredentialResolver value : values)
      if (resolvers.putIfAbsent(value.authMode(), value) != null)
        throw new IllegalStateException(
            "Duplicate Runtime MCP credential resolver: " + value.authMode());
  }

  // 解析本次调用应使用的凭据或组件。在结束时主动释放资源或擦除敏感数据。
  public RuntimeMcpCredential resolve(RuntimeMcpServerDefinition server, long userId) {
    if (server.authMode() == RuntimeMcpAuthMode.NONE) return new RuntimeMcpCredential(null);
    RuntimeMcpCredentialResolver resolver = resolvers.get(server.authMode());
    if (resolver == null)
      throw new IllegalStateException("Runtime MCP credential resolver is unavailable");
    RuntimeMcpCredential credential = resolver.resolve(server, userId);
    if (credential == null || !credential.present()) {
      if (credential != null) credential.close();
      throw new SecurityException("Runtime MCP credential is unavailable");
    }
    return credential;
  }
}
