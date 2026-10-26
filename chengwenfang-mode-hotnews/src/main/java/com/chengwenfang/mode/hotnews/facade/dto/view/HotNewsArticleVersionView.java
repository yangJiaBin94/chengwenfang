package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.util.Map;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点文章版本视图（对齐前端 HotNewsArticleVersion）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsArticleVersionView {
  /** 版本 ID */
  private String id;
  /** 文章 ID */
  private String articleId;
  /** 版本号 */
  private Integer versionNo;
  /** 原稿内容 */
  private String draftContent;
  /** 原稿哈希 */
  private String draftContentHash;
  /** 公众号正文 */
  private String content;
  /** 正文哈希 */
  private String contentHash;
  /** 来源 */
  private String source;
  /** 原因 */
  private String reason;
  /** 工作流运行 ID */
  private String workflowRunId;
  /** 生成参数快照 */
  private Map<String, Object> generationSettings;
  /** 创建时间 */
  private LocalDateTime createdAt;
}
