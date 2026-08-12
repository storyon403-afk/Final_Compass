package cn.finalscompass.ai.runtime.content;

import java.time.Instant;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCenterContentService {
  private static final Set<String> KEYS = Set.of("USAGE_GUIDE", "VCP_INTRO"),
      FORMATS = Set.of("HTML", "MARKDOWN");
  private final JdbcClient jdbc;

  public AiCenterContentService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Page published(String key) {
    validateKey(key);
    return jdbc.sql(
            "SELECT page_key,title,subtitle,content_format,content_body,version,updated_at FROM"
                + " ai_center_content_page WHERE page_key=:key AND status='PUBLISHED'")
        .param("key", key)
        .query(Page.class)
        .optional()
        .orElseThrow(() -> new IllegalArgumentException("AI Center content does not exist"));
  }

  @Transactional
  public Page update(long admin, String key, Update r) {
    validateKey(key);
    String format =
        r == null || r.contentFormat() == null ? "HTML" : r.contentFormat().toUpperCase();
    if (r == null
        || !FORMATS.contains(format)
        || r.title() == null
        || r.title().isBlank()
        || r.title().length() > 200
        || r.subtitle() != null && r.subtitle().length() > 500
        || r.contentBody() == null
        || r.contentBody().isBlank()
        || r.contentBody().length() > 100_000)
      throw new IllegalArgumentException("AI Center content is invalid");
    int changed =
        jdbc.sql(
                """
                UPDATE ai_center_content_page
                SET title=:title,
                    subtitle=:subtitle,
                    content_format=:format,
                    content_body=:content,
                    content_html=:legacy,
                    version=version+1,
                    updated_by=:admin,
                    status='PUBLISHED'
                WHERE page_key=:key
                """)
            .param("title", r.title().trim())
            .param("subtitle", r.subtitle())
            .param("format", format)
            .param("content", r.contentBody())
            .param("legacy", format.equals("HTML") ? r.contentBody() : "<p>Markdown content</p>")
            .param("admin", admin)
            .param("key", key)
            .update();
    if (changed != 1) throw new IllegalArgumentException("AI Center content does not exist");
    return published(key);
  }

  private void validateKey(String key) {
    if (!KEYS.contains(key)) throw new IllegalArgumentException("AI Center content key is invalid");
  }

  public record Page(
      String pageKey,
      String title,
      String subtitle,
      String contentFormat,
      String contentBody,
      int version,
      Instant updatedAt) {}

  public record Update(String title, String subtitle, String contentFormat, String contentBody) {}
}
