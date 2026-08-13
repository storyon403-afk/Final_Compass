package cn.finalscompass.ai.runtime.mcp;

import org.springframework.stereotype.Component;

/**
 * 按用户解析其独立授权的 MCP OAuth 凭据。
 * 维护入口：用户凭据归属规则改这里；授权流程改 RuntimeMcpOAuthService。
 */
@Component
public final class UserRuntimeMcpOAuthCredentialResolver implements RuntimeMcpCredentialResolver {
  private final RuntimeMcpOAuthService oauth;

  public UserRuntimeMcpOAuthCredentialResolver(RuntimeMcpOAuthService oauth) {
    this.oauth = oauth;
  }

  @Override
  public RuntimeMcpAuthMode authMode() {
    return RuntimeMcpAuthMode.USER_OAUTH;
  }

  @Override
  public RuntimeMcpCredential resolve(RuntimeMcpServerDefinition server, long userId) {
    return oauth.resolve(server, userId);
  }
}
