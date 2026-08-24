package cn.finalscompass.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 防止 HTTP 适配器依赖持久化实现细节的轻量架构护栏 */
class ModuleBoundaryTest {
  @Test
  void courseControllerDoesNotDependOnJdbc() throws IOException {
    String source = Files.readString(Path.of(
        "src/main/java/cn/finalscompass/controller/CatalogController.java"));
    assertThat(source).doesNotContain("JdbcClient", ".sql(");
    assertThat(source).contains("CourseCatalogHandler");
  }

  @Test
  void migratedControllersDoNotDependOnJdbc() throws IOException {
    for (String controller : List.of(
        "CatalogController.java", "SurveyController.java", "SystemController.java",
        "CetController.java", "CircleController.java")) {
      String source = Files.readString(Path.of("src/main/java/cn/finalscompass/controller", controller));
      assertThat(source).doesNotContain("JdbcClient", ".sql(");
    }
  }

  @Test
  void uploadControllersUseTheSharedStoragePort() throws IOException {
    String circleHandler = Files.readString(Path.of(
        "src/main/java/cn/finalscompass/circle/application/CircleCommandHandler.java"));
    String cetHandler = Files.readString(Path.of(
        "src/main/java/cn/finalscompass/cet/application/CetItemHandler.java"));
    assertThat(circleHandler).contains("UploadStorage");
    assertThat(cetHandler).contains("UploadStorage");
    assertThat(circleHandler).doesNotContain("Files.createDirectories", ".transferTo(");
    assertThat(cetHandler).doesNotContain("Files.createDirectories", ".transferTo(");
  }
}
