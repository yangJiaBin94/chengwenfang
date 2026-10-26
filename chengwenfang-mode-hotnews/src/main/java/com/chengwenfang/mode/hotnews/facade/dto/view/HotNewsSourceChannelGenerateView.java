package com.chengwenfang.mode.hotnews.facade.dto.view;

import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelView;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 生成来源渠道结果：仅含通过连通性校验并已落库的项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsSourceChannelGenerateView {
  /** 新建成功的渠道。 */
  @Builder.Default
  private List<HotNewsChannelView> created = new ArrayList<>();
  /** 未落库的候选（校验失败 / 去重 / 非法）。 */
  @Builder.Default
  private List<HotNewsSourceChannelSkippedView> skipped = new ArrayList<>();
  /** 是否来自 AI（false 表示未产出可用候选）。 */
  private boolean fromAi;
}
