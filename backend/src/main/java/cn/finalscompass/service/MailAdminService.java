package cn.finalscompass.service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Administrator-only SMTP, template and account-provisioning operations. */
@Service
public class MailAdminService {
  private static final String PASSWORD_ALPHABET =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
  private final JdbcClient jdbc;
  private final MailSecretCipher cipher;
  private final DynamicMailService mail;
  private final TransactionTemplate transactions;
  private final AccountAllocationService accounts;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();
  private final String loginUrl;

  public MailAdminService(
      JdbcClient jdbc,
      MailSecretCipher cipher,
      DynamicMailService mail,
      TransactionTemplate transactions,
      AccountAllocationService accounts,
      @Value("${app.mail.login-url}") String loginUrl) {
    this.jdbc = jdbc;
    this.cipher = cipher;
    this.mail = mail;
    this.transactions = transactions;
    this.accounts = accounts;
    this.loginUrl = loginUrl;
  }

  public Map<String, Object> configuration() {
    List<Map<String, Object>> rows =
        jdbc.sql(
                """
SELECT host,port,security_mode,username,credential_fingerprint,from_address,from_name,reply_to,
       enabled,last_tested_at,last_test_status,updated_at FROM smtp_configuration WHERE id=1
""")
            .query()
            .listOfRows();
    return rows.isEmpty()
        ? Map.of("configured", false, "encryptionAvailable", cipher.available())
        : rows.getFirst();
  }

  public void save(AuthService.CurrentUser admin, SmtpInput input) {
    verifyAdminPassword(admin, input.adminPassword());
    if (!cipher.available())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "未配置邮件加密主密钥");
    requireText(input.host(), "SMTP主机");
    requireText(input.username(), "SMTP用户名");
    requireText(input.credential(), "SMTP授权码");
    requireText(input.fromAddress(), "发件邮箱");
    requireText(input.fromName(), "发件人名称");
    if (input.port() < 1 || input.port() > 65535) throw new IllegalArgumentException("SMTP端口无效");
    if (input.securityMode() == null
        || !List.of("SSL", "STARTTLS").contains(input.securityMode().toUpperCase()))
      throw new IllegalArgumentException("不支持的SMTP加密方式");
    validateHeader(input.fromAddress());
    validateHeader(input.replyTo());
    char[] credential = input.credential().toCharArray();
    try {
      var encrypted = cipher.encrypt(credential);
      jdbc.sql(
              """
INSERT INTO smtp_configuration(id,host,port,security_mode,username,encrypted_credential,credential_iv,
  credential_fingerprint,from_address,from_name,reply_to,enabled,updated_by)
VALUES (1,:host,:port,:mode,:username,:encrypted,:iv,:fingerprint,:fromAddress,:fromName,:replyTo,FALSE,:admin)
ON DUPLICATE KEY UPDATE host=:host,port=:port,security_mode=:mode,username=:username,
  encrypted_credential=:encrypted,credential_iv=:iv,credential_fingerprint=:fingerprint,
  from_address=:fromAddress,from_name=:fromName,reply_to=:replyTo,enabled=FALSE,updated_by=:admin
""")
          .param("host", input.host().trim())
          .param("port", input.port())
          .param("mode", input.securityMode().toUpperCase())
          .param("username", input.username().trim())
          .param("encrypted", encrypted.ciphertext())
          .param("iv", encrypted.iv())
          .param("fingerprint", encrypted.fingerprint())
          .param("fromAddress", input.fromAddress().trim())
          .param("fromName", input.fromName().trim())
          .param("replyTo", blankToNull(input.replyTo()))
          .param("admin", admin.id())
          .update();
    } finally {
      Arrays.fill(credential, '\0');
    }
  }

  public void testAndEnable(AuthService.CurrentUser admin, String adminPassword, String recipient) {
    verifyAdminPassword(admin, adminPassword);
    requireText(recipient, "测试收件邮箱");
    validateHeader(recipient);
    mail.sendTest(recipient);
    jdbc.sql(
            "UPDATE smtp_configuration SET"
                + " enabled=TRUE,last_tested_at=NOW(),last_test_status='SUCCESS',updated_by=:admin"
                + " WHERE id=1")
        .param("admin", admin.id())
        .update();
  }

  public List<Map<String, Object>> templates() {
    return jdbc.sql(
            "SELECT template_type,version,subject_template,text_template,enabled,updated_at FROM"
                + " email_template ORDER BY template_type")
        .query()
        .listOfRows();
  }

  public void updateTemplate(AuthService.CurrentUser admin, String type, TemplateInput input) {
    verifyAdminPassword(admin, input.adminPassword());
    if (!List.of("EMAIL_VERIFICATION", "ACCOUNT_CREDENTIAL").contains(type))
      throw new IllegalArgumentException("不支持的邮件模板");
    requireText(input.subject(), "邮件主题");
    requireText(input.text(), "邮件正文");
    validateHeader(input.subject());
    jdbc.sql(
            """
UPDATE email_template SET version=version+1,subject_template=:subject,text_template=:text,
  enabled=:enabled,updated_by=:admin WHERE template_type=:type
""")
        .param("subject", input.subject().trim())
        .param("text", input.text())
        .param("enabled", input.enabled())
        .param("admin", admin.id())
        .param("type", type)
        .update();
  }

  public Map<String, String> approveAndSend(
      AuthService.CurrentUser admin, long requestId, ProvisionInput input) {
    verifyAdminPassword(admin, input.adminPassword());
    if (!input.confirmed()) throw new IllegalArgumentException("请确认已经人工核实申请人");
    requireText(input.username(), "登录账号");
    requireText(input.displayName(), "显示名");
    if (!input.username().matches("[A-Za-z0-9_.-]{3,64}"))
      throw new IllegalArgumentException("账号只能包含字母、数字、下划线、点或短横线，长度3-64位");
    char[] temporaryPassword = randomPassword();
    Provisioned provisioned;
    try {
      provisioned =
          transactions.execute(
              status -> {
                AccessRow request =
                    jdbc.sql(
                            "SELECT id,email,status FROM beta_access_request WHERE id=:id FOR"
                                + " UPDATE")
                        .param("id", requestId)
                        .query(AccessRow.class)
                        .optional()
                        .orElseThrow(() -> new IllegalArgumentException("申请不存在"));
                if (!"EMAIL_VERIFIED".equals(request.status()))
                  throw new IllegalArgumentException("申请尚未完成邮箱验证或已经处理");
                GeneratedKeyHolder keys = new GeneratedKeyHolder();
                jdbc.sql(
                        """
INSERT INTO app_user(username,password_hash,display_name,role,active,email,must_change_password)
VALUES (:username,:password,:display,'USER',TRUE,:email,TRUE)
""")
                    .param("username", input.username().trim())
                    .param("password", passwords.encode(new String(temporaryPassword)))
                    .param("display", input.displayName().trim())
                    .param("email", request.email())
                    .update(keys, "id");
                long userId = keys.getKey().longValue();
                jdbc.sql(
                        """
                        INSERT INTO anonymous_user(app_user_id,public_id,nickname)
                        VALUES (:user,:publicId,:nickname)
                        """)
                    .param("user", userId)
                    .param("publicId", UUID.randomUUID().toString())
                    .param("nickname", input.displayName().trim())
                    .update();
                jdbc.sql(
                        """
INSERT INTO account_provisioning(request_id,user_id,status,reviewed_by,last_delivery_status)
VALUES (:request,:user,'ACCOUNT_CREATED',:admin,'SENDING')
""")
                    .param("request", requestId)
                    .param("user", userId)
                    .param("admin", admin.id())
                    .update();
                jdbc.sql(
                        "UPDATE beta_access_request SET"
                            + " status='ACCOUNT_CREATED',reviewed_by=:admin,reviewed_at=NOW() WHERE"
                            + " id=:id")
                    .param("admin", admin.id())
                    .param("id", requestId)
                    .update();
                accounts.consumeOrRelease(requestId, input.username().trim());
                return new Provisioned(request.email(), input.username().trim());
              });
      if (provisioned == null) throw new IllegalStateException("账号创建事务失败");
      mail.sendCredential(
          requestId,
          provisioned.email(),
          provisioned.username(),
          temporaryPassword,
          loginUrl,
          admin.id());
      jdbc.sql(
              "UPDATE account_provisioning SET"
                  + " status='CREDENTIAL_SENT',last_delivery_status='SENT',credential_sent_at=NOW()"
                  + " WHERE request_id=:id")
          .param("id", requestId)
          .update();
      jdbc.sql("UPDATE beta_access_request SET status='CREDENTIAL_SENT' WHERE id=:id")
          .param("id", requestId)
          .update();
      return Map.of("status", "CREDENTIAL_SENT", "username", provisioned.username());
    } catch (RuntimeException exception) {
      jdbc.sql("UPDATE account_provisioning SET last_delivery_status='FAILED' WHERE request_id=:id")
          .param("id", requestId)
          .update();
      throw exception;
    } finally {
      Arrays.fill(temporaryPassword, '\0');
    }
  }

  /**
   * Generates a non-identifying display name while leaving the login account
   * administrator-controlled.
   */
  public Map<String, String> suggestDisplayName() {
    List<String> words = List.of("银杏", "松果", "青禾", "白露", "海盐", "星屿", "山岚", "小满");
    String displayName =
        words.get(random.nextInt(words.size())) + " " + (1000 + random.nextInt(9000));
    return Map.of("displayName", displayName);
  }

  private void verifyAdminPassword(AuthService.CurrentUser admin, String password) {
    if (password == null || !passwords.matches(password, admin.passwordHash()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员密码验证失败");
  }

  public void verifyPassword(AuthService.CurrentUser admin, String password) {
    verifyAdminPassword(admin, password);
  }

  private char[] randomPassword() {
    char[] value = new char[16];
    for (int i = 0; i < value.length; i++)
      value[i] = PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length()));
    return value;
  }

  private void validateHeader(String value) {
    if (value != null && (value.contains("\r") || value.contains("\n")))
      throw new IllegalArgumentException("邮件字段不能包含换行符");
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record SmtpInput(
      String host,
      int port,
      String securityMode,
      String username,
      String credential,
      String fromAddress,
      String fromName,
      String replyTo,
      String adminPassword) {}

  public record TemplateInput(String subject, String text, boolean enabled, String adminPassword) {}

  public record ProvisionInput(
      String username, String displayName, boolean confirmed, String adminPassword) {}

  private record AccessRow(long id, String email, String status) {}

  private record Provisioned(String email, String username) {}
}
