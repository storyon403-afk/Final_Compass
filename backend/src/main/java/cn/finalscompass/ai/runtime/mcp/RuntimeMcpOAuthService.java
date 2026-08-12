package cn.finalscompass.ai.runtime.mcp;

import cn.finalscompass.service.AiSecretCipher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public final class RuntimeMcpOAuthService {
  private final JdbcClient jdbc;
  private final StringRedisTemplate redis;
  private final AiSecretCipher cipher;
  private final ObjectMapper json;
  private final String redirectUri;
  private final String statePrefix;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  private final SecureRandom random = new SecureRandom();

  public RuntimeMcpOAuthService(
      JdbcClient jdbc,
      StringRedisTemplate redis,
      AiSecretCipher cipher,
      ObjectMapper json,
      @Value("${app.ai.mcp.oauth.redirect-uri:http://127.0.0.1:8080/api/system/mcp/oauth/callback}")
          String redirectUri,
      @Value("${app.environment:dev}") String environment) {
    this.jdbc = jdbc;
    this.redis = redis;
    this.cipher = cipher;
    this.json = json;
    this.redirectUri = redirectUri;
    this.statePrefix = "fc:" + environment + ":mcp-oauth:";
  }

  public String authorizationUrl(long adminId, String serverKey) {
    OAuthConfig config = config(serverKey);
    if (config.authMode() != RuntimeMcpAuthMode.PLATFORM_OAUTH
        && config.authMode() != RuntimeMcpAuthMode.USER_OAUTH)
      throw new IllegalArgumentException("MCP Server does not use OAuth");
    String state = randomUrl(32), verifier = randomUrl(64);
    String challenge = sha256Url(verifier);
    long subject = config.authMode() == RuntimeMcpAuthMode.PLATFORM_OAUTH ? 0 : adminId;
    try {
      redis
          .opsForValue()
          .set(
              statePrefix + state,
              json.writeValueAsString(
                  Map.of(
                      "adminId",
                      adminId,
                      "serverId",
                      config.id(),
                      "serverKey",
                      config.serverKey(),
                      "subject",
                      subject,
                      "verifier",
                      verifier)),
              Duration.ofMinutes(8));
    } catch (Exception exception) {
      throw new IllegalStateException("MCP OAuth state could not be saved", exception);
    }
    return config.authorizationEndpoint()
        + "?client_id="
        + enc(config.clientId())
        + "&response_type=code&redirect_uri="
        + enc(redirectUri)
        + "&scope="
        + enc(config.scopes())
        + "&state="
        + enc(state)
        + "&code_challenge="
        + enc(challenge)
        + "&code_challenge_method=S256"
        + "&resource="
        + enc(config.endpointUri());
  }

  public String complete(String code, String state) {
    if (code == null || code.isBlank() || state == null || state.isBlank())
      throw new IllegalArgumentException("MCP OAuth callback is invalid");
    String stored = redis.opsForValue().getAndDelete(statePrefix + state);
    if (stored == null) throw new IllegalArgumentException("MCP OAuth state expired");
    try {
      Map<String, Object> values = json.readValue(stored, new TypeReference<>() {});
      long serverId = ((Number) values.get("serverId")).longValue();
      long adminId = ((Number) values.get("adminId")).longValue();
      long subject = ((Number) values.get("subject")).longValue();
      OAuthConfig config = config(serverId);
      Map<String, Object> token =
          token(
              config,
              Map.of(
                  "grant_type",
                  "authorization_code",
                  "code",
                  code,
                  "redirect_uri",
                  redirectUri,
                  "code_verifier",
                  String.valueOf(values.get("verifier"))));
      save(config, subject, adminId, token);
      return config.serverKey();
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("MCP OAuth callback failed", exception);
    }
  }

  public RuntimeMcpCredential resolve(RuntimeMcpServerDefinition server, long userId) {
    long subject = server.authMode() == RuntimeMcpAuthMode.PLATFORM_OAUTH ? 0 : userId;
    TokenRow row =
        jdbc.sql(
                """
SELECT encrypted_access_token,access_token_iv,encrypted_refresh_token,refresh_token_iv,expires_at,connected_by
FROM ai_runtime_mcp_oauth_connection
WHERE server_id=:serverId AND user_id=:userId AND status='CONNECTED'
""")
            .param("serverId", server.id())
            .param("userId", subject)
            .query(TokenRow.class)
            .optional()
            .orElseThrow(() -> new SecurityException("MCP OAuth connection is unavailable"));
    if (row.expiresAt() != null
        && row.expiresAt().toInstant().isBefore(Instant.now().plusSeconds(60))) {
      if (row.encryptedRefreshToken() == null)
        throw new SecurityException("MCP OAuth token expired");
      refresh(server.id(), subject, row);
      return resolve(server, userId);
    }
    char[] access = cipher.decrypt(row.encryptedAccessToken(), row.accessTokenIv());
    try {
      return new RuntimeMcpCredential(access);
    } finally {
      Arrays.fill(access, '\0');
    }
  }

  public void disconnect(String serverKey, long adminId) {
    OAuthConfig config = config(serverKey);
    long subject = config.authMode() == RuntimeMcpAuthMode.PLATFORM_OAUTH ? 0 : adminId;
    disconnect(config.id(), subject);
  }

  private void disconnect(long serverId, long userId) {
    jdbc.sql(
            "UPDATE ai_runtime_mcp_oauth_connection SET status='REVOKED' WHERE server_id=:server"
                + " AND user_id=:user")
        .param("server", serverId)
        .param("user", userId)
        .update();
  }

  private void refresh(long serverId, long subject, TokenRow row) {
    char[] refresh = cipher.decrypt(row.encryptedRefreshToken(), row.refreshTokenIv());
    try {
      OAuthConfig config = config(serverId);
      Map<String, Object> token =
          token(
              config, Map.of("grant_type", "refresh_token", "refresh_token", new String(refresh)));
      save(config, subject, row.connectedBy(), token);
    } finally {
      Arrays.fill(refresh, '\0');
    }
  }

  private void save(OAuthConfig config, long subject, long adminId, Map<String, Object> token) {
    char[] access = required(token, "access_token").toCharArray();
    char[] refresh =
        token.get("refresh_token") == null
            ? null
            : String.valueOf(token.get("refresh_token")).toCharArray();
    try {
      var encryptedAccess = cipher.encrypt(access);
      AiSecretCipher.EncryptedSecret encryptedRefresh =
          refresh == null ? null : cipher.encrypt(refresh);
      long expires = token.get("expires_in") instanceof Number number ? number.longValue() : 3600;
      jdbc.sql(
              """
INSERT INTO ai_runtime_mcp_oauth_connection(
  server_id,user_id,encrypted_access_token,access_token_iv,encrypted_refresh_token,
  refresh_token_iv,token_fingerprint,granted_scopes,expires_at,status,connected_by)
VALUES (:server,:user,:access,:accessIv,:refresh,:refreshIv,:fingerprint,:scopes,
  TIMESTAMPADD(SECOND,:expires,CURRENT_TIMESTAMP(6)),'CONNECTED',:admin)
ON DUPLICATE KEY UPDATE encrypted_access_token=:access,access_token_iv=:accessIv,
  encrypted_refresh_token=COALESCE(:refresh,encrypted_refresh_token),
  refresh_token_iv=COALESCE(:refreshIv,refresh_token_iv),token_fingerprint=:fingerprint,
  granted_scopes=:scopes,expires_at=TIMESTAMPADD(SECOND,:expires,CURRENT_TIMESTAMP(6)),
  status='CONNECTED',connected_by=:admin,refreshed_at=CURRENT_TIMESTAMP(6),last_error_code=NULL
""")
          .param("server", config.id())
          .param("user", subject)
          .param("access", encryptedAccess.ciphertext())
          .param("accessIv", encryptedAccess.iv())
          .param("refresh", encryptedRefresh == null ? null : encryptedRefresh.ciphertext())
          .param("refreshIv", encryptedRefresh == null ? null : encryptedRefresh.iv())
          .param("fingerprint", encryptedAccess.fingerprint())
          .param("scopes", String.valueOf(token.getOrDefault("scope", config.scopes())))
          .param("expires", Math.max(60, Math.min(expires, 86400)))
          .param("admin", adminId)
          .update();
    } finally {
      Arrays.fill(access, '\0');
      if (refresh != null) Arrays.fill(refresh, '\0');
    }
  }

  private Map<String, Object> token(OAuthConfig config, Map<String, String> fields) {
    try {
      Map<String, String> form = new LinkedHashMap<>(fields);
      form.put("client_id", config.clientId());
      form.put("scope", config.scopes());
      form.put("resource", config.endpointUri());
      String body =
          form.entrySet().stream()
              .map(entry -> enc(entry.getKey()) + "=" + enc(entry.getValue()))
              .collect(java.util.stream.Collectors.joining("&"));
      HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(URI.create(config.tokenEndpoint()))
                  .timeout(Duration.ofSeconds(15))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2)
        throw new IllegalStateException("MCP OAuth token exchange rejected");
      return json.readValue(response.body(), new TypeReference<>() {});
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("MCP OAuth token exchange failed", exception);
    }
  }

  private OAuthConfig config(String key) {
    return jdbc.sql(
            """
            SELECT id,server_key,auth_mode,endpoint_uri,oauth_authorization_endpoint,
              oauth_token_endpoint,oauth_client_id,oauth_scopes
            FROM ai_runtime_mcp_server WHERE server_key=:key AND status='ACTIVE'
            """)
        .param("key", key)
        .query(OAuthConfig.class)
        .optional()
        .orElseThrow(() -> new IllegalStateException("MCP OAuth Server is unavailable"));
  }

  private OAuthConfig config(long id) {
    return jdbc.sql(
            """
            SELECT id,server_key,auth_mode,endpoint_uri,oauth_authorization_endpoint,
              oauth_token_endpoint,oauth_client_id,oauth_scopes
            FROM ai_runtime_mcp_server WHERE id=:id AND status='ACTIVE'
            """)
        .param("id", id)
        .query(OAuthConfig.class)
        .single();
  }

  private String required(Map<String, Object> token, String field) {
    Object value = token.get(field);
    if (value == null || String.valueOf(value).isBlank())
      throw new IllegalStateException("MCP OAuth token is incomplete");
    return String.valueOf(value);
  }

  private String randomUrl(int bytes) {
    byte[] value = new byte[bytes];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private String sha256Url(String value) {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private record OAuthConfig(
      long id,
      String serverKey,
      RuntimeMcpAuthMode authMode,
      String endpointUri,
      String authorizationEndpoint,
      String tokenEndpoint,
      String clientId,
      String scopes) {
    OAuthConfig {
      validate(authorizationEndpoint);
      validate(tokenEndpoint);
    }

    private static void validate(String value) {
      URI uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getFragment() != null)
        throw new IllegalStateException("MCP OAuth endpoint is invalid");
    }
  }

  private record TokenRow(
      String encryptedAccessToken,
      String accessTokenIv,
      String encryptedRefreshToken,
      String refreshTokenIv,
      java.sql.Timestamp expiresAt,
      long connectedBy) {}
}
