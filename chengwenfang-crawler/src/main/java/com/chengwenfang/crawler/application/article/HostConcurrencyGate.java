package com.chengwenfang.crawler.application.article;

import cn.hutool.core.util.StrUtil;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * 按主机限制文章抓取并发，降低同站打爆风险。
 */
@Slf4j
public final class HostConcurrencyGate {
  /** host → 信号量。 */
  private final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();
  /** 每主机许可数。 */
  private final int permits;

  /**
   * @param permits 每主机并发上限；≤0 时按 1
   */
  public HostConcurrencyGate(int permits) {
    this.permits = Math.max(1, permits);
  }

  /**
   * 在主机闸门内执行动作。
   *
   * @param url      目标 URL
   * @param action   动作
   * @param <T>      返回类型
   * @return 动作结果
   */
  public <T> T run(String url, Supplier<T> action) {
    String host = hostKey(url);
    Semaphore semaphore = semaphores.computeIfAbsent(host, ignored -> new Semaphore(permits));
    boolean acquired = false;
    try {
      semaphore.acquire();
      acquired = true;
      return action.get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      log.warn("文章抓取主机闸门被中断 host={}", host);
      throw new IllegalStateException("文章抓取主机闸门被中断: " + host, interrupted);
    } finally {
      if (acquired) {
        semaphore.release();
      }
    }
  }

  /**
   * @param url 链接
   * @return 主机键；无法解析时用 {@code unknown}
   */
  public static String hostKey(String url) {
    if (StrUtil.isBlank(url)) {
      return "unknown";
    }
    try {
      String raw = url.trim();
      if (!raw.contains("://")) {
        raw = "https://" + raw;
      }
      URI uri = URI.create(raw);
      String host = uri.getHost();
      if (StrUtil.isBlank(host)) {
        return "unknown";
      }
      return host.toLowerCase();
    } catch (Exception ignored) {
      return "unknown";
    }
  }
}
