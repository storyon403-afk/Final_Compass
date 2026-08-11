package cn.finalscompass.ai.runtime.provider.client;

import java.util.Arrays;

public final class RuntimeBinaryInput implements AutoCloseable {
    private final String mediaType;
    private final byte[] bytes;

    public RuntimeBinaryInput(String mediaType, byte[] bytes) {
        if (mediaType == null || !mediaType.matches("^(image|audio|application)/[A-Za-z0-9.+-]{1,80}$"))
            throw new IllegalArgumentException("Runtime binary media type is invalid");
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Runtime binary input is empty");
        this.mediaType = mediaType;
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }
    public String mediaType() { return mediaType; }
    public int size() { return bytes.length; }
    public byte[] copyBytes() { return Arrays.copyOf(bytes, bytes.length); }
    @Override public void close() { Arrays.fill(bytes, (byte) 0); }
}
