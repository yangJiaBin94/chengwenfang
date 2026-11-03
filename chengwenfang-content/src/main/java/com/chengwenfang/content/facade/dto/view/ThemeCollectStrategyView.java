package com.chengwenfang.content.facade.dto.view;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.enums.HotNewsCollectStrategyMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题采集策略（结构化 / 非结构化）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeCollectStrategyView {
  /** 策略模式持久化值。 */
  private String mode;
  /** AI 推荐理由（可展示）。 */
  private String rationale;
  /** 用户是否改过 AI 推荐。 */
  private boolean userOverride;

  /**
   * 解析为枚举；缺省 STRUCTURED。
   *
   * @return 模式
   */
  public HotNewsCollectStrategyMode resolveMode() {
    return HotNewsCollectStrategyMode.from(mode).orElse(HotNewsCollectStrategyMode.STRUCTURED);
  }

  /**
   * 是否非结构化。
   *
   * @return true 表示 UNSTRUCTURED
   */
  public boolean isUnstructured() {
    return resolveMode() == HotNewsCollectStrategyMode.UNSTRUCTURED;
  }

  /**
   * 是否已选定模式。
   *
   * @return 有 mode 则为 true
   */
  public boolean hasMode() {
    return StrUtil.isNotBlank(mode);
  }
}
