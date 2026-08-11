package cn.finalscompass.ai.runtime.mcp;

import java.util.Arrays;

public final class RuntimeMcpCredential implements AutoCloseable {
    private final char[] accessToken;

    public RuntimeMcpCredential(char[] accessToken) {
        this.accessToken = accessToken == null ? null : Arrays.copyOf(accessToken, accessToken.length);
    }
    public char[] accessToken() {
        return accessToken == null ? null : Arrays.copyOf(accessToken, accessToken.length);
    }
    public boolean present() { return accessToken != null && accessToken.length > 0; }
    @Override public void close() {
        if (accessToken != null) Arrays.fill(accessToken, '\0');
    }
}
