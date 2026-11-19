package com.chengwenfang.knowledge.domain.model;

import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 切块滑窗参数（按字符）；由 {@link com.chengwenfang.knowledge.domain.rule.ChunkParamsResolver} 按形态解析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkParams {

  /** 目标块长。 */
  private int targetChars;
  /** 超过此长度才滑窗。 */
  private int maxChars;
  /** 相邻窗口重叠。 */
  private int overlapChars;
  /** 过短块合并阈值。 */
  private int minMergeChars;

  /**
   * 默认参数（与 Markdown / 普通 Plain 对齐）。
   *
   * @return 参数
   */
  public static KnowledgeChunkParams defaults() {
    return KnowledgeChunkParams.builder()
        .targetChars(KnowledgeChunkConstants.TARGET_CHARS)
        .maxChars(KnowledgeChunkConstants.MAX_CHARS)
        .overlapChars(KnowledgeChunkConstants.OVERLAP_CHARS)
        .minMergeChars(KnowledgeChunkConstants.MIN_MERGE_CHARS)
        .build();
  }
}
