package com.chengwenfang.content.facade.dto.view;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题主要意图（用户确认后驱动采集/过滤/补话题）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeCoreIntentView {
  /** 一句话意图摘要。 */
  private String summary;
  /** 要什么素材。 */
  @Builder.Default
  private List<String> materialWants = new ArrayList<>();
  /** 不要什么素材。 */
  @Builder.Default
  private List<String> materialAvoids = new ArrayList<>();
  /** 好素材长什么样（验收句）。 */
  private String successLooksLike;
  /** 用户是否已确认。 */
  private boolean confirmed;

  /**
   * 是否具备可用摘要。
   *
   * @return 有摘要则为 true
   */
  public boolean hasSummary() {
    return StrUtil.isNotBlank(summary);
  }

  /**
   * 拼装注入 AI 的意图短文。
   *
   * @return 提示片段；无摘要时为空串
   */
  public String toPromptBlock() {
    if (!hasSummary()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("【主要意图】").append(StrUtil.trim(summary));
    if (CollUtil.isNotEmpty(materialWants)) {
      builder.append("\n要的素材：").append(String.join("、", materialWants));
    }
    if (CollUtil.isNotEmpty(materialAvoids)) {
      builder.append("\n不要的素材：").append(String.join("、", materialAvoids));
    }
    if (StrUtil.isNotBlank(successLooksLike)) {
      builder.append("\n好素材标准：").append(StrUtil.trim(successLooksLike));
    }
    return builder.toString().trim();
  }
}
