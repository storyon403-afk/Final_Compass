package cn.finalscompass.shared.storage;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

/** 以服务端生成名称寻址上传对象的存储端口 */
public interface UploadStorage {
  Path resolve(String storageName);
  boolean exists(String storageName);
  void store(String storageName, MultipartFile source) throws IOException;
  void deleteQuietly(String storageName);
}
