package com.chengwenfang.mode.hotnews.application.research;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料补充 Agent 单轮模型输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchAgentTurnResponse {
  /** 本轮动作列表。 */
  @Builder.Default
  private List<ResearchAgentAction> actions = new ArrayList<>();
}
