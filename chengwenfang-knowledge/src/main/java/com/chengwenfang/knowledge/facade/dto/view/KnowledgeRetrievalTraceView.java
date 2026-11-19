package com.chengwenfang.knowledge.facade.dto.view;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索轨迹只读视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRetrievalTraceView {
  /** 轨迹 ID。 */
  private String id;
  /** 会话 ID。 */
  private String sessionId;
  /** 轮次。 */
  private Integer roundNo;
  /** 模式。 */
  private String modeKey;
  /** 查询。 */
  private String queryText;
  /** 耗时毫秒。 */
  private Integer latencyMs;
  /** 工具步骤。 */
  private List<KnowledgeToolStepView> toolSteps;
  /** 创建时间。 */
  private LocalDateTime createdAt;
}
