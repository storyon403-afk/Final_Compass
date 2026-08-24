package cn.finalscompass.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 通过当前已启用且加密保存的 SMTP 配置发送纯文本邮件 */
@Service
public class DynamicMailService {
  private static final Logger log = LoggerFactory.getLogger(DynamicMailService.class);
  private final JdbcClient jdbc;
  private final MailSecretCipher cipher;
  private final MicrosoftGraphMailService microsoft;

  public DynamicMailService(
      JdbcClient jdbc, MailSecretCipher cipher, MicrosoftGraphMailService microsoft) {
    this.jdbc = jdbc;
    this.cipher = cipher;
    this.microsoft = microsoft;
  }

  public void sendVerification(long requestId, String recipient, String code) {
    send(
        requestId,
        recipient,
        "EMAIL_VERIFICATION",
        Map.of("verificationCode", code, "expiresMinutes", "10"),
        null);
  }

  public void sendCredential(
      long requestId,
      String recipient,
      String username,
      char[] temporaryPassword,
      String loginUrl,
      long adminId) {
    send(
        requestId,
        recipient,
        "ACCOUNT_CREDENTIAL",
        Map.of(
            "username",
            username,
            "temporaryPassword",
            new String(temporaryPassword),
            "loginUrl",
            loginUrl),
        adminId);
  }

  public void sendTest(String recipient) {
    SmtpRow config = enabledConfiguration(false);
    deliver(
        config,
        recipient,
        "Finals Compass SMTP 测试",
        "这是一封SMTP配置测试邮件。收到此邮件表示当前配置可正常发送。\n\nFinals Compass");
  }

  private void send(
      long requestId, String recipient, String type, Map<String, String> values, Long adminId) {
    TemplateRow template =
        jdbc.sql(
                """
                SELECT template_type,version,subject_template,text_template FROM email_template
                WHERE template_type=:type AND enabled=TRUE
                """)
            .param("type", type)
            .query(TemplateRow.class)
            .optional()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮件模板尚未启用"));
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
INSERT INTO email_delivery_log(request_id,delivery_type,recipient_hash,template_type,template_version,status,requested_by)
VALUES (:request,:type,:recipient,:template,:version,'SENDING',:admin)
""")
        .param("request", requestId)
        .param("type", type)
        .param("recipient", digest(recipient))
        .param("template", template.templateType())
        .param("version", template.version())
        .param("admin", adminId)
        .update(keys, "id");
    long logId = keys.getKey().longValue();
    try {
      String subject = render(template.subjectTemplate(), values),
          text = render(template.textTemplate(), values);
      if (microsoft.active()) microsoft.send(recipient, subject, text);
      else deliver(enabledConfiguration(true), recipient, subject, text);
      jdbc.sql("UPDATE email_delivery_log SET status='SENT',sent_at=NOW() WHERE id=:id")
          .param("id", logId)
          .update();
    } catch (RuntimeException exception) {
      jdbc.sql(
              "UPDATE email_delivery_log SET status='FAILED',error_code='SMTP_SEND_FAILED' WHERE"
                  + " id=:id")
          .param("id", logId)
          .update();
      throw exception;
    }
  }

  private void deliver(SmtpRow config, String recipient, String subject, String text) {
    if (subject.contains("\r") || subject.contains("\n"))
      throw new IllegalArgumentException("邮件主题不能包含换行符");
    char[] credential = cipher.decrypt(config.encryptedCredential(), config.credentialIv());
    try {
      JavaMailSenderImpl sender = new JavaMailSenderImpl();
      sender.setHost(config.host());
      sender.setPort(config.port());
      sender.setUsername(config.username());
      sender.setPassword(new String(credential));
      sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
      Properties properties = sender.getJavaMailProperties();
      properties.put("mail.smtp.auth", "true");
      properties.put("mail.smtp.connectiontimeout", "5000");
      properties.put("mail.smtp.timeout", "10000");
      properties.put("mail.smtp.writetimeout", "10000");
      if ("STARTTLS".equals(config.securityMode()))
        properties.put("mail.smtp.starttls.enable", "true");
      if ("SSL".equals(config.securityMode())) properties.put("mail.smtp.ssl.enable", "true");
      var message = sender.createMimeMessage();
      var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setFrom(config.fromAddress(), config.fromName());
      helper.setTo(recipient);
      if (config.replyTo() != null && !config.replyTo().isBlank())
        helper.setReplyTo(config.replyTo());
      helper.setSubject(subject);
      helper.setText(text, false);
      sender.send(message);
    } catch (Exception exception) {
      log.warn(
          "SMTP delivery failed: {}: {}",
          exception.getClass().getSimpleName(),
          safeMessage(exception));
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮件发送失败，请联系管理员检查SMTP配置");
    } finally {
      Arrays.fill(credential, '\0');
    }
  }

  private SmtpRow enabledConfiguration(boolean requireEnabled) {
    String sql =
        "SELECT"
            + " host,port,security_mode,username,encrypted_credential,credential_iv,from_address,from_name,reply_to,enabled"
            + " FROM smtp_configuration WHERE id=1"
            + (requireEnabled ? " AND enabled=TRUE" : "");
    return jdbc.sql(sql)
        .query(SmtpRow.class)
        .optional()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "管理员尚未配置并启用SMTP"));
  }

  private String render(String template, Map<String, String> values) {
    String rendered = template;
    for (var entry : values.entrySet())
      rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
    if (rendered.matches("(?s).*\\{\\{[^}]+}}.*"))
      throw new IllegalArgumentException("邮件模板包含未提供的变量");
    return rendered;
  }

  private String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.toLowerCase().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String safeMessage(Throwable exception) {
    StringBuilder result = new StringBuilder();
    for (Throwable current = exception;
        current != null && result.length() < 1000;
        current = current.getCause()) {
      if (current.getMessage() == null || current.getMessage().isBlank()) continue;
      if (!result.isEmpty()) result.append(" | ");
      result.append(
          current
              .getMessage()
              .replaceAll("(?i)(password|credential|authorization)=[^,;\\s]+", "$1=[REDACTED]"));
    }
    return result.toString();
  }

  private record TemplateRow(
      String templateType, int version, String subjectTemplate, String textTemplate) {}

  private record SmtpRow(
      String host,
      int port,
      String securityMode,
      String username,
      String encryptedCredential,
      String credentialIv,
      String fromAddress,
      String fromName,
      String replyTo,
      boolean enabled) {}
}
