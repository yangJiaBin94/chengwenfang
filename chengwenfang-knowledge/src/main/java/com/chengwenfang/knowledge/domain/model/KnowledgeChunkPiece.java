package com.chengwenfang.knowledge.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 切块规则输出的内存片段（尚未落库）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkPiece {
  /** 块序号（从 0 起）。 */
  private Integer chunkIndex;
  /** 标题路径（Markdown 章节约束）。 */
  private String headingPath;
  /** 块正文。 */
  private String content;
  /** 字数估计（非空白字符近似）。 */
  private Integer tokenEstimate;
  /** 送入嵌入模型的拼接文本。 */
  private String embeddingText;
}
