package com.chengwenfang.knowledge.facade.dto;

import com.chengwenfang.knowledge.facade.dto.view.KnowledgeToolStepView;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索轨迹 {@code payload_json} 结构化载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRetrievalTracePayload {
  /** 命中切块 ID。 */
  private List<String> chunkIds;
  /** 是否经过精排。 */
  private Boolean reranked;
  /** 拒答原因。 */
  private String abstainReason;
  /** 原问题。 */
  private String question;
  /** 降级原因。 */
  private List<String> degradedReasons;
  /** 工具步骤。 */
  private List<KnowledgeToolStepView> tools;
}
