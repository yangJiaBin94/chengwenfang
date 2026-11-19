package com.chengwenfang.knowledge.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 三存储对账结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeReconcileResultView {
  /** 是否试跑（未真正删库/入队）。 */
  private Boolean dryRun;
  /** 删除的孤儿切块数。 */
  private Integer orphanChunksRemoved;
  /** 清理图边的文档数。 */
  private Integer graphDocumentsCleared;
  /** 入队重建文档数。 */
  private Integer reindexEnqueued;
  /** 入队文档 ID。 */
  private List<String> enqueuedDocumentIds;
}
