package com.workbench.publishing.facade.spi;

import com.workbench.publishing.facade.dto.view.PublishingRenderView;

/**
 * 产出渲染扩展点：按格式编码将正文渲染为渠道可用形态（如 article → 公众号 HTML）。
 *
 * <p>首批实现为文章 HTML 渲染；新增格式时实现本接口并注册即可。
 */
public interface OutputRenderer {

  /**
   * 产出格式编码（与 {@code output_formats.code} 一致）。
   *
   * @return 格式 code
   */
  String formatCode();

  /**
   * 按主题将 Markdown 渲染为可用 HTML。
   *
   * @param markdown 原文 Markdown；null 按空串
   * @param themeKey 主题版本 ID 或主题 key；空则用默认空配置
   * @return 含 html、哈希、主题信息的渲染视图
   */
  PublishingRenderView render(String markdown, String themeKey);
}
