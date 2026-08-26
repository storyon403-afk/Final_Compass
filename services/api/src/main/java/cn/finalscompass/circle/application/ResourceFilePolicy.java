package cn.finalscompass.circle.application;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Uploaded learning-resource types are determined by the server, never by client MIME headers. */
final class ResourceFilePolicy {
  private static final Set<String> ZIP_TYPES = Set.of("zip", "docx", "pptx");
  private static final Set<String> OLE_TYPES = Set.of("doc", "ppt");
  private static final Map<String, String> MIME_TYPES = Map.ofEntries(
      Map.entry("pdf", "application/pdf"),
      Map.entry("doc", "application/msword"),
      Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
      Map.entry("ppt", "application/vnd.ms-powerpoint"),
      Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
      Map.entry("zip", "application/zip"),
      Map.entry("png", "image/png"),
      Map.entry("jpg", "image/jpeg"),
      Map.entry("jpeg", "image/jpeg"));

  private ResourceFilePolicy() {}

  static String validateAndMime(String extension, byte[] header) {
    String ext = String.valueOf(extension).toLowerCase(Locale.ROOT);
    String mime = MIME_TYPES.get(ext);
    if (mime == null || !matches(ext, header)) {
      throw new IllegalArgumentException("文件内容与扩展名不匹配");
    }
    return mime;
  }

  static boolean mayRenderInline(String mime) {
    return "application/pdf".equals(mime) || mime.startsWith("image/");
  }

  private static boolean matches(String ext, byte[] value) {
    if ("pdf".equals(ext)) return startsWith(value, 0x25, 0x50, 0x44, 0x46, 0x2d);
    if ("png".equals(ext)) return startsWith(value, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
    if ("jpg".equals(ext) || "jpeg".equals(ext)) return startsWith(value, 0xff, 0xd8, 0xff);
    if (OLE_TYPES.contains(ext)) return startsWith(value, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
    if (ZIP_TYPES.contains(ext)) {
      return startsWith(value, 0x50, 0x4b, 0x03, 0x04)
          || startsWith(value, 0x50, 0x4b, 0x05, 0x06)
          || startsWith(value, 0x50, 0x4b, 0x07, 0x08);
    }
    return false;
  }

  private static boolean startsWith(byte[] value, int... signature) {
    if (value == null || value.length < signature.length) return false;
    for (int index = 0; index < signature.length; index++) {
      if ((value[index] & 0xff) != signature[index]) return false;
    }
    return true;
  }
}
