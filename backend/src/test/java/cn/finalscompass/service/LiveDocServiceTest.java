package cn.finalscompass.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LiveDocServiceTest {
  @Test void validatesProjectContainers() {
    var project = LiveDocService.validate("自然演示.vpptx", "VPPTX", new byte[]{1,2,3});
    assertEquals("vpptx", project.kind());
    assertThrows(IllegalArgumentException.class, () -> LiveDocService.validate("x", "pptx", new byte[]{1}));
    assertThrows(IllegalArgumentException.class, () -> LiveDocService.validate("x", "vdocx", new byte[0]));
  }

  @Test void migrationCreatesUserScopedBlobStorage() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/db/migration/V72__livedoc_project_artifacts.sql"));
    assertTrue(sql.contains("CREATE TABLE livedoc_project"));
    assertTrue(sql.contains("content LONGBLOB NOT NULL"));
    assertTrue(sql.contains("INDEX idx_livedoc_project_user_updated"));
  }
}
