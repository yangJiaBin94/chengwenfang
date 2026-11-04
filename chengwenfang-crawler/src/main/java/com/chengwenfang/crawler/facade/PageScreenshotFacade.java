package com.chengwenfang.crawler.facade;

import com.chengwenfang.crawler.facade.dto.view.PageScreenshotResult;
import java.util.List;

/**
 * 网页分段截图门面（本机 Playwright CLI）。
 */
public interface PageScreenshotFacade {

  /**
   * 是否启用截图能力。
   *
   * @return 启用时 true
   */
  boolean isEnabled();

  /**
   * 对 URL 自上而下分段截图。
   *
   * @param url 页面地址
   * @return 结果；未启用或失败时 {@code ok=false}
   */
  PageScreenshotResult capture(String url);

  /**
   * 删除截图文件及空父目录。
   *
   * @param paths PNG 路径
   */
  void cleanup(List<String> paths);
}
