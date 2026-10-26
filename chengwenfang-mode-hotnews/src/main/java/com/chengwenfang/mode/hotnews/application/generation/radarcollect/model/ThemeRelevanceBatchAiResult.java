package com.chengwenfang.mode.hotnews.application.generation.radarcollect.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主题相关性整包判定的固定 JSON 结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeRelevanceBatchAiResult {
  /** 明显偏题的候选临时 ID。拿不准的不要列入。 */
  @Builder.Default
  private List<String> offTopicIds = new ArrayList<>();
}
