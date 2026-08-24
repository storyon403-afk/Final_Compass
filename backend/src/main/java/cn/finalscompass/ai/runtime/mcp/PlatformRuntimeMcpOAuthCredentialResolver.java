package cn.finalscompass.ai.runtime.mcp;

import org.springframework.stereotype.Component;

/**
 * 为 MCP 调用解析平台统一配置的 OAuth 凭据
 * 维护入口：平台令牌的选择规则改这里；授权码交换与令牌存储改 RuntimeMcpOAuthService
 */
@Component
public final class PlatformRuntimeMcpOAuthCredentialResolver
    implements RuntimeMcpCredentialResolver {
  private final RuntimeMcpOAuthService oauth;

  public PlatformRuntimeMcpOAuthCredentialResolver(RuntimeMcpOAuthService oauth) {
    this.oauth = oauth;
  }

  @Override
  public RuntimeMcpAuthMode authMode() {
    return RuntimeMcpAuthMode.PLATFORM_OAUTH;
  }

  @Override
  public RuntimeMcpCredential resolve(RuntimeMcpServerDefinition server, long userId) {
    return oauth.resolve(server, 0);
  }
}
