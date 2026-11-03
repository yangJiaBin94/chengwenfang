package com.chengwenfang.mode.hotnews.application.search;

import com.chengwenfang.mode.hotnews.facade.enums.TavilySearchScene;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tavily 配置装配上下文（场景 + 可变输入）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchContext {
  /** 装配场景。 */
  private TavilySearchScene scene;
  /** 采集时间窗（小时）；可空。 */
  private Integer hours;
  /** 优选域名（补搜策略）；可空。 */
  @Builder.Default
  private List<String> includeDomains = new ArrayList<>();
  /** 额外屏蔽域名（并入默认视频域）；可空。 */
  @Builder.Default
  private List<String> excludeDomains = new ArrayList<>();
  /** 覆盖检索词；非空时替换渠道 queries。 */
  @Builder.Default
  private List<String> queries = new ArrayList<>();
  /** 是否覆盖 topic 为 finance。 */
  private boolean financeTopic;
}
