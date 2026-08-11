package cn.finalscompass.ai.runtime.mcp;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;

@Component
public final class RuntimeMcpCredentialResolverRegistry {
    private final EnumMap<RuntimeMcpAuthMode, RuntimeMcpCredentialResolver> resolvers =
            new EnumMap<>(RuntimeMcpAuthMode.class);

    public RuntimeMcpCredentialResolverRegistry(List<RuntimeMcpCredentialResolver> values) {
        for (RuntimeMcpCredentialResolver value : values)
            if (resolvers.putIfAbsent(value.authMode(), value) != null)
                throw new IllegalStateException("Duplicate Runtime MCP credential resolver: " + value.authMode());
    }

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
