package cn.finalscompass.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts SMTP credentials and OAuth refresh tokens with a mail-specific AES-256-GCM master key.
 */
@Component
public class MailSecretCipher {
  private final String configuredKey;
  private final SecureRandom random = new SecureRandom();

  public MailSecretCipher(@Value("${app.mail.encryption-key:}") String configuredKey) {
    this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
  }

  public boolean available() {
    return !configuredKey.isBlank();
  }

  public EncryptedSecret encrypt(char[] secret) {
    if (secret == null || secret.length < 4 || secret.length > 8192)
      throw new IllegalArgumentException("邮件服务密钥长度不合法");
    byte[] plain = new String(secret).getBytes(StandardCharsets.UTF_8);
    byte[] iv = new byte[12];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
      return new EncryptedSecret(
          Base64.getEncoder().encodeToString(cipher.doFinal(plain)),
          Base64.getEncoder().encodeToString(iv),
          fingerprint(secret));
    } catch (Exception exception) {
      throw new IllegalStateException("邮件服务密钥加密失败", exception);
    } finally {
      Arrays.fill(plain, (byte) 0);
    }
  }

  public char[] decrypt(String encrypted, String iv) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.getDecoder().decode(iv)));
      byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encrypted));
      try {
        return new String(plain, StandardCharsets.UTF_8).toCharArray();
      } finally {
        Arrays.fill(plain, (byte) 0);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("邮件服务密钥解密失败，请检查邮件主密钥", exception);
    }
  }

  private SecretKeySpec key() {
    if (!available()) throw new IllegalStateException("服务器未配置 MAIL_SECRET_ENCRYPTION_KEY");
    byte[] decoded = Base64.getDecoder().decode(configuredKey);
    if (decoded.length != 32)
      throw new IllegalStateException("MAIL_SECRET_ENCRYPTION_KEY解码后必须为32字节");
    return new SecretKeySpec(decoded, "AES");
  }

  private String fingerprint(char[] value) throws Exception {
    byte[] bytes = new String(value).getBytes(StandardCharsets.UTF_8);
    try {
      return java.util.HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
          .substring(0, 12);
    } finally {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  public record EncryptedSecret(String ciphertext, String iv, String fingerprint) {}
}
