package cn.finalscompass.service;

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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Microsoft 授权码与 PKCE 连接及 Graph 邮件发送提供方 */
@Service
public class MicrosoftGraphMailService {
  private static final Logger log = LoggerFactory.getLogger(MicrosoftGraphMailService.class);
  private static final String AUTHORIZE =
      "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
  private static final String TOKEN = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
  private static final String SCOPES = "openid profile email offline_access User.Read Mail.Send";
  private final JdbcClient jdbc;
  private final StringRedisTemplate redis;
  private final MailSecretCipher cipher;
  private final ObjectMapper json;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  private final SecureRandom random = new SecureRandom();
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;
  private final String redisPrefix;

  public MicrosoftGraphMailService(
      JdbcClient jdbc,
      StringRedisTemplate redis,
      MailSecretCipher cipher,
      ObjectMapper json,
      @Value("${app.mail.microsoft.client-id:}") String clientId,
      @Value("${app.mail.microsoft.client-secret:}") String clientSecret,
      @Value("${app.mail.microsoft.redirect-uri:}") String redirectUri,
      @Value("${app.environment:dev}") String environment) {
    this.jdbc = jdbc;
    this.redis = redis;
    this.cipher = cipher;
    this.json = json;
    this.clientId = clientId.trim();
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri.trim();
    this.redisPrefix = "fc:" + environment + ":mail-oauth:microsoft:";
  }

  public Map<String, Object> status() {
    List<Map<String, Object>> rows =
        jdbc.sql(
                """
SELECT account_email,account_name,status,connected_at,last_refreshed_at,last_success_at,last_error_code
FROM mail_oauth_connection WHERE provider='MICROSOFT_GRAPH'
""")
            .query()
            .listOfRows();
    boolean active = active();
    if (rows.isEmpty())
      return Map.of("configured", configured(), "connected", false, "active", false);
    var result = new java.util.LinkedHashMap<String, Object>(rows.getFirst());
    result.put("configured", configured());
    result.put("connected", true);
    result.put("active", active);
    return result;
  }

  public String authorizationUrl(AuthService.CurrentUser admin) {
    requireConfigured();
    String state = randomUrl(32), verifier = randomUrl(64);
    String challenge;
    try {
      challenge =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  MessageDigest.getInstance("SHA-256")
                      .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
    redis
        .opsForValue()
        .set(redisPrefix + state, admin.id() + ":" + verifier, Duration.ofMinutes(5));
    return AUTHORIZE
        + "?client_id="
        + enc(clientId)
        + "&response_type=code&redirect_uri="
        + enc(redirectUri)
        + "&response_mode=query&scope="
        + enc(SCOPES)
        + "&state="
        + enc(state)
        + "&code_challenge="
        + enc(challenge)
        + "&code_challenge_method=S256&prompt=select_account";
  }

  public void complete(AuthService.CurrentUser admin, String code, String state) {
    requireConfigured();
    String stored = redis.opsForValue().getAndDelete(redisPrefix + state);
    if (stored == null || !stored.startsWith(admin.id() + ":"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微软授权状态已失效，请重新连接");
    Map<String, Object> token =
        token(
            Map.of(
                "client_id",
                clientId,
                "client_secret",
                clientSecret,
                "grant_type",
                "authorization_code",
                "code",
                code,
                "redirect_uri",
                redirectUri,
                "code_verifier",
                stored.substring(stored.indexOf(':') + 1),
                "scope",
                SCOPES));
    String access = required(token, "access_token"), refresh = required(token, "refresh_token");
    Map<String, Object> me =
        graphGet(
            access,
            "https://graph.microsoft.com/v1.0/me?$select=displayName,mail,userPrincipalName");
    String email = String.valueOf(me.get("mail"));
    if (email.equals("null") || email.isBlank()) email = required(me, "userPrincipalName");
    char[] secret = refresh.toCharArray();
    try {
      var encrypted = cipher.encrypt(secret);
      jdbc.sql(
              """
INSERT INTO mail_oauth_connection(id,provider,account_email,account_name,encrypted_refresh_token,
  refresh_token_iv,token_fingerprint,granted_scopes,status,connected_by)
VALUES (1,'MICROSOFT_GRAPH',:email,:name,:token,:iv,:fingerprint,:scopes,'CONNECTED',:admin)
ON DUPLICATE KEY UPDATE account_email=:email,account_name=:name,encrypted_refresh_token=:token,
  refresh_token_iv=:iv,token_fingerprint=:fingerprint,granted_scopes=:scopes,status='CONNECTED',
  connected_by=:admin,connected_at=NOW(),last_error_code=NULL
""")
          .param("email", email)
          .param("name", String.valueOf(me.getOrDefault("displayName", "Finals Compass")))
          .param("token", encrypted.ciphertext())
          .param("iv", encrypted.iv())
          .param("fingerprint", encrypted.fingerprint())
          .param("scopes", String.valueOf(token.getOrDefault("scope", SCOPES)))
          .param("admin", admin.id())
          .update();
    } finally {
      Arrays.fill(secret, '\0');
    }
  }

  public void testAndEnable(AuthService.CurrentUser admin, String recipient) {
    send(
        recipient,
        "Finals Compass Microsoft Graph 测试",
        "Microsoft OAuth2 发信连接正常。\n\nFinals Compass");
    jdbc.sql(
            "UPDATE mail_provider_setting SET active_provider='MICROSOFT_GRAPH',updated_by=:admin"
                + " WHERE id=1")
        .param("admin", admin.id())
        .update();
  }

  public void disconnect(AuthService.CurrentUser admin) {
    jdbc.sql("DELETE FROM mail_oauth_connection WHERE provider='MICROSOFT_GRAPH'").update();
    jdbc.sql("UPDATE mail_provider_setting SET active_provider='SMTP',updated_by=:admin WHERE id=1")
        .param("admin", admin.id())
        .update();
  }

  public boolean active() {
    if (!configured()) return false;
    return jdbc.sql(
            """
            SELECT EXISTS(
              SELECT 1 FROM mail_provider_setting p
              JOIN mail_oauth_connection c ON c.provider='MICROSOFT_GRAPH' AND c.status='CONNECTED'
              WHERE p.id=1 AND p.active_provider='MICROSOFT_GRAPH'
            )
            """)
        .query(Boolean.class)
        .single();
  }

  public void send(String recipient, String subject, String text) {
    requireConfigured();
    Connection row =
        jdbc.sql(
                """
                SELECT encrypted_refresh_token,refresh_token_iv FROM mail_oauth_connection
                WHERE provider='MICROSOFT_GRAPH' AND status='CONNECTED'
                """)
            .query(Connection.class)
            .optional()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微软邮箱尚未连接"));
    char[] refreshChars = cipher.decrypt(row.encryptedRefreshToken(), row.refreshTokenIv());
    try {
      Map<String, Object> token =
          token(
              Map.of(
                  "client_id",
                  clientId,
                  "client_secret",
                  clientSecret,
                  "grant_type",
                  "refresh_token",
                  "refresh_token",
                  new String(refreshChars),
                  "scope",
                  SCOPES));
      rotateRefreshToken(token);
      String body =
          json.writeValueAsString(
              Map.of(
                  "message",
                  Map.of(
                      "subject",
                      subject,
                      "body",
                      Map.of("contentType", "Text", "content", text),
                      "toRecipients",
                      List.of(Map.of("emailAddress", Map.of("address", recipient)))),
                  "saveToSentItems",
                  true));
      HttpRequest request =
          HttpRequest.newBuilder(URI.create("https://graph.microsoft.com/v1.0/me/sendMail"))
              .timeout(Duration.ofSeconds(15))
              .header("Authorization", "Bearer " + required(token, "access_token"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 202)
        throw graphFailure("GRAPH_SEND_" + response.statusCode(), response.body());
      jdbc.sql(
              "UPDATE mail_oauth_connection SET last_success_at=NOW(),last_error_code=NULL WHERE"
                  + " provider='MICROSOFT_GRAPH'")
          .update();
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      log.warn("Microsoft Graph mail failed: {}", exception.getClass().getSimpleName());
      throw graphFailure("GRAPH_SEND_FAILED", null);
    } finally {
      Arrays.fill(refreshChars, '\0');
    }
  }

  private void rotateRefreshToken(Map<String, Object> token) {
    Object rotated = token.get("refresh_token");
    if (rotated == null) {
      jdbc.sql(
              "UPDATE mail_oauth_connection SET last_refreshed_at=NOW() WHERE"
                  + " provider='MICROSOFT_GRAPH'")
          .update();
      return;
    }
    char[] value = String.valueOf(rotated).toCharArray();
    try {
      var encrypted = cipher.encrypt(value);
      jdbc.sql(
              """
UPDATE mail_oauth_connection SET encrypted_refresh_token=:token,refresh_token_iv=:iv,
  token_fingerprint=:fingerprint,last_refreshed_at=NOW() WHERE provider='MICROSOFT_GRAPH'
""")
          .param("token", encrypted.ciphertext())
          .param("iv", encrypted.iv())
          .param("fingerprint", encrypted.fingerprint())
          .update();
    } finally {
      Arrays.fill(value, '\0');
    }
  }

  private Map<String, Object> token(Map<String, String> form) {
    try {
      String body =
          form.entrySet().stream()
              .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
              .collect(java.util.stream.Collectors.joining("&"));
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(TOKEN))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      Map<String, Object> parsed = json.readValue(response.body(), new TypeReference<>() {});
      if (response.statusCode() / 100 != 2)
        throw graphFailure(
            "OAUTH_TOKEN_" + response.statusCode(), String.valueOf(parsed.get("error")));
      return parsed;
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw graphFailure("OAUTH_TOKEN_FAILED", null);
    }
  }

  private Map<String, Object> graphGet(String access, String uri) {
    try {
      HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(URI.create(uri))
                  .timeout(Duration.ofSeconds(10))
                  .header("Authorization", "Bearer " + access)
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2)
        throw graphFailure("GRAPH_PROFILE_" + response.statusCode(), response.body());
      return json.readValue(response.body(), new TypeReference<>() {});
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw graphFailure("GRAPH_PROFILE_FAILED", null);
    }
  }

  private ResponseStatusException graphFailure(String code, String detail) {
    try {
      jdbc.sql(
              "UPDATE mail_oauth_connection SET last_error_code=:code WHERE"
                  + " provider='MICROSOFT_GRAPH'")
          .param("code", code)
          .update();
    } catch (Exception ignored) {
    }
    if (detail != null) log.warn("Microsoft mail operation failed: {}", code);
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微软邮件服务暂不可用（" + code + "）");
  }

  private boolean configured() {
    return !clientId.isBlank()
        && !clientSecret.isBlank()
        && !redirectUri.isBlank()
        && cipher.available();
  }

  private void requireConfigured() {
    if (!configured())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "服务器尚未配置 Microsoft OAuth2 应用");
  }

  private String required(Map<String, Object> values, String key) {
    Object value = values.get(key);
    if (value == null || String.valueOf(value).isBlank())
      throw graphFailure("MISSING_" + key.toUpperCase(), null);
    return String.valueOf(value);
  }

  private String randomUrl(int bytes) {
    byte[] value = new byte[bytes];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private record Connection(String encryptedRefreshToken, String refreshTokenIv) {}
}
