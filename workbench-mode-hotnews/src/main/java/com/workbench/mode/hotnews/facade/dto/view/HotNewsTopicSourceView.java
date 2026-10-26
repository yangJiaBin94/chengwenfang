package com.workbench.mode.hotnews.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题关联信源视图（对齐前端 HotNewsTopicSource）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsTopicSourceView {
  /** 关联 ID */
  private String id;
  /** 选题 ID */
  private String topicId;
  /** 信源条目 ID */
  private String sourceItemId;
  /** 角色 */
  private String role;
  /** 标题 */
  private String title;
  /** 事实速览 / AI 概要（来自信源 summary） */
  private String summary;
  /** 链接 */
  private String url;
  /** 来源名（频道名） */
  private String sourceName;
  /** 站点来源展示名（B站/YouTube 等） */
  private String siteLabel;
  /** 媒体类型 article/video */
  private String mediaType;
  /** 生命周期 ready/pending_parse/discarded */
  private String lifecycleStatus;
  /** 发布时间 */
  private LocalDateTime publishedAt;
}
