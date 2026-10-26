package com.workbench.crawler.facade.constant;

import cn.hutool.core.util.StrUtil;

/**
 * 文章正文 Lightpanda 调用模式（开集常量）。
 */
public final class ArticleBrowserModes {
  private ArticleBrowserModes() {}

  /** 通过 {@code docker exec} 调用容器内 {@code lightpanda fetch}（Windows 推荐）。 */
  public static final String DOCKER_EXEC = "docker-exec";
  /** 直接调用本机 {@code lightpanda} 可执行文件（Linux/macOS/WSL）。 */
  public static final String BINARY = "binary";

  /**
   * 规范化模式；无法识别时回退 {@link #DOCKER_EXEC}。
   *
   * @param raw 配置值
   * @return 规范化模式
   */
  public static String normalize(String raw) {
    if (StrUtil.isBlank(raw)) {
      return DOCKER_EXEC;
    }
    String value = raw.trim().toLowerCase();
    if (BINARY.equals(value)) {
      return BINARY;
    }
    return DOCKER_EXEC;
  }

  /**
   * @param raw 配置值
   * @return 是否为 docker-exec
   */
  public static boolean isDockerExec(String raw) {
    return DOCKER_EXEC.equals(normalize(raw));
  }

  /**
   * @param raw 配置值
   * @return 是否为 binary
   */
  public static boolean isBinary(String raw) {
    return BINARY.equals(normalize(raw));
  }
}
