package cn.finalscompass.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 只允许明确配置的反向代理声明客户端地址，防止伪造 X-Forwarded-For 绕过限流。 */
@Service
public final class TrustedProxyService {
  private final Set<String> trustedAddresses;

  public TrustedProxyService(
      @Value("${app.trusted-proxy-addresses:127.0.0.1,::1}") String configuredAddresses) {
    this.trustedAddresses =
        Arrays.stream(configuredAddresses.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(TrustedProxyService::normalizeLiteral)
            .collect(Collectors.toUnmodifiableSet());
    if (trustedAddresses.isEmpty()) {
      throw new IllegalArgumentException("At least one trusted proxy address is required");
    }
  }

  public String clientIp(HttpServletRequest request) {
    String directAddress = normalizeOrFallback(request.getRemoteAddr(), "unknown");
    if (!trustedAddresses.contains(directAddress)) return directAddress;

    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.isBlank()) return directAddress;
    return normalizeOrFallback(forwarded.split(",", 2)[0].trim(), directAddress);
  }

  private static String normalizeOrFallback(String address, String fallback) {
    try {
      return normalizeLiteral(address);
    } catch (IllegalArgumentException exception) {
      return fallback;
    }
  }

  private static String normalizeLiteral(String address) {
    String value = String.valueOf(address).trim();
    if (value.isEmpty()
        || value.contains("%")
        || !value.matches("[0-9A-Fa-f:.]+")) {
      throw new IllegalArgumentException("Trusted proxy entries must be literal IP addresses");
    }
    try {
      return InetAddress.getByName(value).getHostAddress();
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid IP address: " + value, exception);
    }
  }
}
