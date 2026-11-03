package com.chengwenfang.mode.hotnews.application.research;

import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料补充 Agent 采集结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchAgentCollectResult {
  /** 是否走过 Agent 路径（含中途失败后的空结果）。 */
  private boolean usedAgent;
  /** 是否成功 finish 且产出候选。 */
  private boolean finished;
  /** 模型轮次。 */
  private int rounds;
  /** 执行过的搜索词。 */
  @Builder.Default
  private List<String> queriesRun = new ArrayList<>();
  /** 原始命中条数（去重后）。 */
  private int hitCount;
  /** 选用的网页候选。 */
  @Builder.Default
  private List<ResearchCandidate> webCandidates = new ArrayList<>();
}
