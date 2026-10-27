package com.chengwenfang.content.facade.spi;

import com.chengwenfang.content.facade.dto.view.ThemeListMetrics;
import java.util.Map;
import java.util.Set;

/**
 * 主题列表指标扩展点：模式层按主题批量贡献来源/候选等计数。
 * <p>content 组装主题视图时调用；实现方不得反向依赖 content 的 infrastructure。
 */
public interface ThemeListMetricsContributor {

  /**
   * 是否负责该模式编码的主题指标。
   *
   * @param modeCode 模式 code（与 content_modes.code 一致）
   * @return 本贡献者应处理时为 true
   */
  boolean supportsMode(String modeCode);

  /**
   * 按主题 id 批量统计指标。
   *
   * @param themeIds 主题 id 集合；空集时返回空 Map
   * @return themeId → 指标；无数据的主题可省略（调用方按 0 处理）
   */
  Map<String, ThemeListMetrics> countByThemeIds(Set<String> themeIds);
}
