package cn.finalscompass.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class LocalUploadStorageTest {
  @TempDir Path root;

  @Test
  void storesAndDeletesObjectInsideConfiguredRoot() throws Exception {
    var storage = new LocalUploadStorage(root.toString());
    var source = new MockMultipartFile("file", "note.txt", "text/plain", "content".getBytes());

    storage.store("generated-name.txt", source);

    assertThat(storage.exists("generated-name.txt")).isTrue();
    assertThat(Files.readString(storage.resolve("generated-name.txt"))).isEqualTo("content");
    storage.deleteQuietly("generated-name.txt");
    assertThat(storage.exists("generated-name.txt")).isFalse();
  }

  @Test
  void rejectsPathsOutsideConfiguredRoot() {
    var storage = new LocalUploadStorage(root.toString());
    assertThatThrownBy(() -> storage.resolve("../escaped.txt"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("文件路径不安全");
  }
}
