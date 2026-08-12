package cn.finalscompass.ai.runtime.mcp;

import org.springframework.stereotype.Component;

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
