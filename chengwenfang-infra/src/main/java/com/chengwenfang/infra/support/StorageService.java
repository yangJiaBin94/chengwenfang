package com.chengwenfang.infra.support;

import com.chengwenfang.infra.config.ChengwenfangProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

/**
 * 运行时目录与内容根路径解析服务。
 * <p>区分 {@code runtimeDir}（生成物/缓存）与 {@code contentRoot}（仓库内容），并防止路径穿越。
 */
@Component
public class StorageService {
  private final Path root;
  private final Path contentRoot;

  public StorageService(ChengwenfangProperties properties) {
    this.root = Paths.get(properties.getRuntimeDir()).toAbsolutePath().normalize();
    this.contentRoot = Paths.get(properties.getContentRoot()).toAbsolutePath().normalize();
  }

  /** 返回运行时根目录（绝对路径）。 */
  public Path root() {
    return root;
  }

  /** 返回内容根目录（绝对路径），用于解析仓库内 content/public 等路径。 */
  public Path contentRoot() {
    return contentRoot;
  }

  /**
   * 返回生成图片目录；若不存在则创建。
   *
   * @return {@code runtimeDir/generated-images}
   */
  public Path generatedImagesDir() {
    Path dir = root.resolve("generated-images");
    try {
      Files.createDirectories(dir);
    } catch (Exception exception) {
      throw new IllegalStateException("无法创建配图目录：" + dir, exception);
    }
    return dir;
  }

  /**
   * 将相对路径解析到运行时根下。
   *
   * @param relative 相对 {@code runtimeDir} 的路径
   */
  public Path resolve(String relative) {
    return root.resolve(relative).normalize();
  }

  /**
   * 将仓库相对路径解析到 contentRoot 下，并拒绝逃逸出内容根的路径。
   *
   * @param relative 相对内容根的路径（支持正反斜杠）
   * @throws IllegalArgumentException 路径为空或发生路径穿越时
   */
  public Path resolveContent(String relative) {
    if (relative == null || relative.isBlank()) {
      throw new IllegalArgumentException("content path 不能为空");
    }
    String cleaned = relative.replace('\\', '/');
    while (cleaned.startsWith("/")) {
      cleaned = cleaned.substring(1);
    }
    Path resolved = contentRoot.resolve(cleaned).normalize();
    if (!resolved.startsWith(contentRoot)) {
      throw new IllegalArgumentException("content path 越出内容根目录：" + relative);
    }
    return resolved;
  }
}
