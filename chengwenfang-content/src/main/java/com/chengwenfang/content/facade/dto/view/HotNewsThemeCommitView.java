package com.chengwenfang.content.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题确认启用结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsThemeCommitView {
  /** 主题 id。 */
  private String themeId;
  /** 启用后状态。 */
  private String status;
  /** 本次创建的初始渠道数量。 */
  private int channelCount;
  /** 本次创建的渠道类型列表。 */
  @Builder.Default
  private List<String> seededChannelTypes = new ArrayList<>();
}
