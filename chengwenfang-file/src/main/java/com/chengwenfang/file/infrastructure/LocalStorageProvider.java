package com.chengwenfang.file.infrastructure;

import com.chengwenfang.common.BusinessException;
import com.chengwenfang.infra.support.StorageService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.stereotype.Component;
import com.chengwenfang.file.facade.StorageProvider;
import com.chengwenfang.file.facade.enums.StorageType;

/**
 * 本地磁盘存储实现。
 * <p>{@code objectKey} 为仓库/运行时相对路径（如 {@code content/...} 或 {@code generated-images/...}）；
 * 读路径时会尝试 {@code public/content/...} 镜像。
 */
@Component
public class LocalStorageProvider implements StorageProvider {
  private final StorageService storage;

  public LocalStorageProvider(StorageService storage) {
    this.storage = storage;
  }

  /** 返回 LOCAL 存储类型。 */
  @Override
  public StorageType type() {
    return StorageType.LOCAL;
  }

  /** 写入字节到可写路径（generated-images / content 等）。 */
  @Override
  public String store(String objectKey, byte[] bytes) {
    Path path = resolveWritable(objectKey);
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      return normalizeKey(objectKey);
    } catch (Exception exception) {
      throw new BusinessException("file_store_failed", exception.getMessage(), 500);
    }
  }

  /** 打开已存在对象的输入流。 */
  @Override
  public InputStream open(String objectKey) {
    Path path = resolveExisting(objectKey);
    try {
      return Files.newInputStream(path);
    } catch (Exception exception) {
      throw new BusinessException("file_open_failed", exception.getMessage(), 500);
    }
  }

  /** 解析可读本地绝对路径；缺失则 404。 */
  @Override
  public Path resolveLocalPath(String objectKey) {
    return resolveExisting(objectKey);
  }

  /** 判断对象是否已作为普通文件存在。 */
  @Override
  public boolean exists(String objectKey) {
    try {
      return Files.isRegularFile(resolveCandidate(objectKey));
    } catch (Exception ignored) {
      return false;
    }
  }

  /** 尽力删除文件（失败忽略）。 */
  @Override
  public void delete(String objectKey) {
    try {
      Files.deleteIfExists(resolveCandidate(objectKey));
    } catch (Exception ignored) {
      // best-effort
    }
  }

  /** 解析写入路径：generated-images/.chengwenfang 走运行时根，其余走 content。 */
  private Path resolveWritable(String objectKey) {
    String key = normalizeKey(objectKey);
    if (key.startsWith("generated-images/") || key.startsWith(".chengwenfang/")) {
      String relative = key.startsWith(".chengwenfang/") ? key.substring(".chengwenfang/".length()) : key;
      return storage.resolve(relative);
    }
    return storage.resolveContent(key);
  }

  private Path resolveExisting(String objectKey) {
    Path path = resolveCandidate(objectKey);
    if (!Files.isRegularFile(path)) {
      throw new BusinessException("file_missing", "文件不存在：" + objectKey, 404);
    }
    return path;
  }

  /**
   * 解析候选路径：优先 content 主路径，再尝试 public/content 镜像与去前缀变体。
   */
  private Path resolveCandidate(String objectKey) {
    String key = normalizeKey(objectKey);
    if (key.startsWith("generated-images/") || key.startsWith(".chengwenfang/")) {
      String relative = key.startsWith(".chengwenfang/") ? key.substring(".chengwenfang/".length()) : key;
      return storage.resolve(relative);
    }
    Path primary = storage.resolveContent(key);
    if (Files.isRegularFile(primary)) {
      return primary;
    }
    // 兼容仓库内 public/content 镜像与以 /content 开头的 key
    if (key.startsWith("content/")) {
      Path publicMirror = storage.resolveContent("public/" + key);
      if (Files.isRegularFile(publicMirror)) {
        return publicMirror;
      }
    }
    if (key.startsWith("/content/")) {
      Path stripped = storage.resolveContent(key.substring(1));
      if (Files.isRegularFile(stripped)) {
        return stripped;
      }
      Path publicMirror = storage.resolveContent("public" + key);
      if (Files.isRegularFile(publicMirror)) {
        return publicMirror;
      }
    }
    return primary;
  }

  private static String normalizeKey(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      throw new BusinessException("file_key_required", "objectKey 不能为空", 400);
    }
    return objectKey.replace('\\', '/');
  }
}
