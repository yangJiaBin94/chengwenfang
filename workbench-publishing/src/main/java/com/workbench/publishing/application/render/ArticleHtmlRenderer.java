package com.workbench.publishing.application.render;

import cn.hutool.core.util.StrUtil;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.workbench.content.facade.constant.OutputFormatCodes;
import com.workbench.publishing.application.LayoutThemeService;
import com.workbench.publishing.domain.styling.ThemeHtmlStylerUtil;
import com.workbench.publishing.facade.constant.LayoutThemeKeys;
import com.workbench.publishing.facade.dto.view.PublishingRenderView;
import com.workbench.publishing.facade.spi.OutputRenderer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 文章格式渲染器：Markdown → 公众号兼容 HTML（套排版主题样式）。
 */
@Component
public class ArticleHtmlRenderer implements OutputRenderer {

  /** 排版主题配置加载。 */
  private final LayoutThemeService layoutThemes;

  /**
   * @param layoutThemes 排版主题服务
   */
  public ArticleHtmlRenderer(LayoutThemeService layoutThemes) {
    this.layoutThemes = layoutThemes;
  }

  /**
   * {@inheritDoc}
   *
   * @return {@link OutputFormatCodes#ARTICLE}
   */
  @Override
  public String formatCode() {
    return OutputFormatCodes.ARTICLE;
  }

  /**
   * 按主题将 Markdown 渲染为公众号兼容 HTML。
   *
   * @param markdown 原文 Markdown；null 按空串
   * @param themeKey 主题版本 ID 或主题 key；空/blank/null 字符串则用默认空配置
   * @return 含 html、哈希、主题信息的渲染视图
   */
  @Override
  public PublishingRenderView render(String markdown, String themeKey) {
    // 规范化入参：空 markdown / 空 theme 走默认
    String md = markdown == null ? "" : markdown;
    String themeVersionId = themeKey == null
            || StrUtil.isBlank(themeKey)
            || "null".equals(themeKey)
        ? ""
        : themeKey;
    // 加载主题 config（版本 id 优先，失败再按 key）
    Map<String, Object> config = layoutThemes.loadThemeConfig(themeVersionId);
    MutableDataSet options = new MutableDataSet();
    Parser parser = Parser.builder(options).build();
    HtmlRenderer renderer = HtmlRenderer.builder(options).build();
    // Markdown → HTML 正文，再套主题样式
    Node document = parser.parse(md);
    String body = renderer.render(document);
    String html = ThemeHtmlStylerUtil.style(body, config);
    String themeNameKey = String.valueOf(config.getOrDefault("_themeKey",
        StrUtil.isBlank(themeVersionId) ? LayoutThemeKeys.DEFAULT_KEY : themeVersionId));
    return PublishingRenderView.builder()
        .html(html)
        .htmlHash(sha256(html))
        .themeKey(themeNameKey)
        .plainText(md)
        .markdownHash(sha256(md))
        .themeVersionId(themeVersionId)
        .images(List.of())
        .diagnostics(List.of())
        .build();
  }

  /**
   * 计算字符串 SHA-256 十六进制摘要。
   *
   * @param value 原文
   * @return hex 摘要；算法不可用时返回空串
   */
  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ignored) {
      return "";
    }
  }
}
