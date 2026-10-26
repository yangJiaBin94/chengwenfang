package com.chengwenfang.publishing.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推送公众号草稿请求（由编排层解析图片后调用门面，或由 Controller 直接传入 Markdown）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpDraftPushRequest {
  /**
   * 成文坊文章 ID；有值时同文复推覆盖已绑定的公众号草稿。
   */
  private String articleId;
  /** 统一文档 ID（公众号轨）；空则服务端按 articleId 兜底。 */
  private String documentId;
  /** 文章 Markdown 正文（将按主题渲染为 HTML）。 */
  private String markdown;
  /** 排版主题版本 ID。 */
  private String themeVersionId;
  /** 标题；空则从 Markdown 首个标题推断。 */
  private String title;
  /** 摘要；空则截取正文纯文本前段。 */
  private String digest;
  /** 作者；空则用配置中的默认作者。 */
  private String author;
  /** 封面图资源地址（成文坊相对路径或绝对 URL）；空则取正文第一张图。 */
  private String coverSrc;
}
