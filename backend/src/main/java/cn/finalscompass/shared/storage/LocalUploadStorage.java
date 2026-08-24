package cn.finalscompass.shared.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 将对象限制在配置上传根目录内的本地文件系统适配器 */
@Component
public class LocalUploadStorage implements UploadStorage {
  private final Path root;

  public LocalUploadStorage(@Value("${app.upload-dir}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  @Override
  public Path resolve(String storageName) {
    Path path = root.resolve(storageName).normalize();
    if (!path.startsWith(root)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件路径不安全");
    }
    return path;
  }

  @Override
  public boolean exists(String storageName) {
    return Files.isRegularFile(resolve(storageName));
  }

  @Override
  public void store(String storageName, MultipartFile source) throws IOException {
    Files.createDirectories(root);
    source.transferTo(resolve(storageName));
  }

  @Override
  public void deleteQuietly(String storageName) {
    try {
      Files.deleteIfExists(resolve(storageName));
    } catch (IOException ignored) {
      // 清理操作采用尽力而为策略，数据库仍是权威状态来源
    }
  }
}
