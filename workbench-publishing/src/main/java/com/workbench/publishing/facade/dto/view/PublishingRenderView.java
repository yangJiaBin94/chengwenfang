package com.workbench.publishing.facade.dto.view;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 排版渲染结果（对齐前端 PublishingRenderData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishingRenderView {
  /** 渲染 HTML。 */
  private String html;
  /** HTML 哈希。 */
  private String htmlHash;
  /** 主题 key。 */
  private String themeKey;
  /** 纯文本/原文。 */
  private String plainText;
  /** Markdown 哈希。 */
  private String markdownHash;
  /** 主题版本 ID。 */
  private String themeVersionId;
  /** 图片清单。 */
  private List<Map<String, Object>> images;
  /** 诊断信息。 */
  private List<Map<String, Object>> diagnostics;
}