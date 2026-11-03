package com.chengwenfang.mode.hotnews.application.research;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选题资料补充的来源策略（优选域名 / 屏蔽域 / 角度词）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchSourcePolicy {
  /** 策略包标识，如 finance。 */
  private String packKey;
  /** 优选域名（不含协议），用于 site: 检索。 */
  @Builder.Default
  private List<String> preferredDomains = new ArrayList<>();
  /** 屏蔽或降权域名。 */
  @Builder.Default
  private List<String> blockedDomains = new ArrayList<>();
  /** 检索角度词（与主体拼接）。 */
  @Builder.Default
  private List<String> queryAngles = new ArrayList<>();
  /** 是否对优选域生成 site: 查询。 */
  @Builder.Default
  private boolean siteSearchEnabled = true;
  /** 是否仍允许无 site 的开放搜索。 */
  @Builder.Default
  private boolean openSearchEnabled = true;

  /**
   * @return 是否配置了可用的优选域名
   */
  public boolean hasPreferredDomains() {
    return preferredDomains != null && !preferredDomains.isEmpty();
  }
}
