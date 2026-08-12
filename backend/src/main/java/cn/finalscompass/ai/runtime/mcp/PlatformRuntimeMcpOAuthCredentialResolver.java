package cn.finalscompass.ai.runtime.mcp;

import org.springframework.stereotype.Component;

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
