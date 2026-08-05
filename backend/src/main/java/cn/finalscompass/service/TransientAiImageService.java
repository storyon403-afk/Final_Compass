package cn.finalscompass.service;

import cn.finalscompass.ai.provider.AiProviderAdapter;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Set;

/** Decodes one request-scoped camera image in memory. It never writes to the upload directory. */
@Service
public class TransientAiImageService {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public AiProviderAdapter.TransientImage decode(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return null;
        int comma = dataUrl.indexOf(',');
        if (comma < 1 || !dataUrl.substring(0, comma).endsWith(";base64")) throw new IllegalArgumentException("临时图片格式不正确");
        String mediaType = dataUrl.substring(5, dataUrl.indexOf(';')).toLowerCase();
        if (!TYPES.contains(mediaType)) throw new IllegalArgumentException("只支持 JPEG、PNG 或 WebP 临时图片");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("临时图片编码不正确"); }
        if (bytes.length == 0 || bytes.length > MAX_BYTES || !matches(mediaType, bytes)) {
            java.util.Arrays.fill(bytes, (byte) 0);
            throw new IllegalArgumentException("临时图片无效或超过 4MB");
        }
        return new AiProviderAdapter.TransientImage(mediaType, bytes);
    }

    private boolean matches(String type, byte[] value) {
        return switch (type) {
            case "image/jpeg" -> value.length > 2 && (value[0] & 255) == 0xff && (value[1] & 255) == 0xd8;
            case "image/png" -> value.length > 7 && (value[0] & 255) == 0x89 && value[1] == 0x50 && value[2] == 0x4e && value[3] == 0x47;
            case "image/webp" -> value.length > 11 && value[0] == 'R' && value[1] == 'I' && value[2] == 'F' && value[3] == 'F'
                    && value[8] == 'W' && value[9] == 'E' && value[10] == 'B' && value[11] == 'P';
            default -> false;
        };
    }
}
