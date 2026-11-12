package com.chengwenfang.crawler.application;

import com.chengwenfang.crawler.application.article.PageScreenshotClient;
import com.chengwenfang.crawler.facade.PageScreenshotFacade;
import com.chengwenfang.crawler.facade.dto.view.PageScreenshotResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link PageScreenshotFacade} 默认实现：委托本机 CLI 客户端。
 */
@Service
@RequiredArgsConstructor
public class DefaultPageScreenshotFacade implements PageScreenshotFacade {

  /** 本机 Playwright 截图客户端。 */
  private final PageScreenshotClient client;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isEnabled() {
    return client.isEnabled();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PageScreenshotResult capture(String url) {
    return client.capture(url);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PageScreenshotResult captureEvidence(String url, String findText) {
    return client.captureEvidence(url, findText);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void cleanup(List<String> paths) {
    client.cleanup(paths);
  }
}
