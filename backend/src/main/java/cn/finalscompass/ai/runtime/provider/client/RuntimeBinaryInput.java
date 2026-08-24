package cn.finalscompass.ai.runtime.provider.client;

import java.util.Arrays;

/**
 * 封装模型调用携带的二进制内容，并通过复制与擦除降低敏感数据残留风险
 * 维护入口：新增媒体类型或调整大小限制时修改这里及各供应商客户端
 */
public final class RuntimeBinaryInput implements AutoCloseable {
  private final String mediaType;
  private final byte[] bytes;

  public RuntimeBinaryInput(String mediaType, byte[] bytes) {
    if (mediaType == null || !mediaType.matches("^(image|audio|application)/[A-Za-z0-9.+-]{1,80}$"))
      throw new IllegalArgumentException("Runtime binary media type is invalid");
    if (bytes == null || bytes.length == 0)
      throw new IllegalArgumentException("Runtime binary input is empty");
    this.mediaType = mediaType;
    this.bytes = Arrays.copyOf(bytes, bytes.length);
  }

  public String mediaType() {
    return mediaType;
  }

  public int size() {
    return bytes.length;
  }

  public byte[] copyBytes() {
    return Arrays.copyOf(bytes, bytes.length);
  }

  // 清除内存中的敏感凭据。在结束时主动释放资源或擦除敏感数据
  @Override
  public void close() {
    Arrays.fill(bytes, (byte) 0);
  }
}
