package com.workbench.mode.hotnews.application.generation.radarcollect.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 主题种子素材 Skill 的固定 JSON 结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiThemeSeedAiResult {
  /** 选题标题。 */
  private String title;
  /** 中文素材速览。 */
  private String summary;
  /** 写作角度信号。 */
  @Builder.Default
  private List<String> signals = new ArrayList<>();
  /** 推荐切口。 */
  private String angle;
}
