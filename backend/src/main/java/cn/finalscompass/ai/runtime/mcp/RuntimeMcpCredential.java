package cn.finalscompass.ai.runtime.mcp;

import java.util.Arrays;

/**
 * 封装 MCP 调用使用的访问令牌，并在关闭时擦除内存副本
 * 维护入口：令牌字段或敏感数据清理方式变化时修改这里
 */
public final class RuntimeMcpCredential implements AutoCloseable {
  private final char[] accessToken;

  public RuntimeMcpCredential(char[] accessToken) {
    this.accessToken = accessToken == null ? null : Arrays.copyOf(accessToken, accessToken.length);
  }

  public char[] accessToken() {
    return accessToken == null ? null : Arrays.copyOf(accessToken, accessToken.length);
  }

  public boolean present() {
    return accessToken != null && accessToken.length > 0;
  }

  // 清除内存中的敏感凭据。在结束时主动释放资源或擦除敏感数据
  @Override
  public void close() {
    if (accessToken != null) Arrays.fill(accessToken, '\0');
  }
}
