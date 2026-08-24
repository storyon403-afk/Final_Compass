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

@Component
public class AiSecretCipher {
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private final String configuredKey;
  private final SecureRandom random = new SecureRandom();

  public AiSecretCipher(@Value("${app.ai.encryption-key:}") String configuredKey) {
    this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
  }

  public boolean available() {
    return !configuredKey.isBlank();
  }

  public EncryptedSecret encrypt(char[] secret) {
    requireSecret(secret);
    byte[] plain = new String(secret).getBytes(StandardCharsets.UTF_8);
    byte[] iv = new byte[12];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(plain);
      return new EncryptedSecret(
          Base64.getEncoder().encodeToString(encrypted),
          Base64.getEncoder().encodeToString(iv),
          fingerprint(secret));
    } catch (IllegalStateException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("API Key 加密失败", exception);
    } finally {
      Arrays.fill(plain, (byte) 0);
    }
  }

  public char[] decrypt(String encrypted, String iv) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.getDecoder().decode(iv)));
      byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encrypted));
      try {
        return new String(plain, StandardCharsets.UTF_8).toCharArray();
      } finally {
        Arrays.fill(plain, (byte) 0);
      }
    } catch (IllegalStateException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("API Key 解密失败，请检查服务器加密主密钥", exception);
    }
  }

  public String fingerprint(char[] secret) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(new String(secret).getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest).substring(0, 12);
    } catch (Exception exception) {
      throw new IllegalStateException("无法生成 Key 指纹", exception);
    }
  }

  private SecretKeySpec key() {
    if (!available())
      throw new IllegalStateException("服务器未配置 AI_SECRET_ENCRYPTION_KEY，不能保存 API Key");
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(configuredKey);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("AI_SECRET_ENCRYPTION_KEY 必须是 Base64", exception);
    }
    if (decoded.length != 32)
      throw new IllegalStateException("AI_SECRET_ENCRYPTION_KEY 解码后必须为 32 字节");
    return new SecretKeySpec(decoded, "AES");
  }

  private void requireSecret(char[] secret) {
    if (secret == null || secret.length < 8 || secret.length > 500)
      throw new IllegalArgumentException("API Key 长度不合法");
  }

  public record EncryptedSecret(String ciphertext, String iv, String fingerprint) {}
}
