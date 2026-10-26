package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点文章改写候选结果视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsRewriteResultView {
  /** 文章 ID */
  private String id;
  /** 公众号轨统一文档 ID */
  private String documentId;
  /** 草稿轨统一文档 ID */
  private String draftDocumentId;
  /** 选题 ID */
  private String topicId;
  /** 简报 ID */
  private String briefId;
  /** 标题 */
  private String title;
  /** 原稿内容 */
  private String draftContent;
  /** 原稿哈希 */
  private String draftContentHash;
  /** 原稿字数 */
  private Integer draftWordCount;
  /** 公众号正文 */
  private String content;
  /** 正文哈希 */
  private String contentHash;
  /** 正文字数 */
  private Integer wordCount;
  /** 当前版本号 */
  private Integer currentVersionNo;
  /** 状态 */
  private String status;
  /** 工作流运行 ID */
  private String workflowRunId;
  /** 来源快照 */
  private List<HotNewsBriefSourceRow> sourceSnapshot;
  /** 生成参数 */
  private Map<String, Object> generationSettings;
  /** 创建时间 */
  private LocalDateTime createdAt;
  /** 更新时间 */
  private LocalDateTime updatedAt;
  /** 改写指令 */
  private String instruction;
  /** 是否已改写 */
  private Boolean rewritten;
}
