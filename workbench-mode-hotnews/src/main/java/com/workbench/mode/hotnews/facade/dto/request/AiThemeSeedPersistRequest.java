package com.workbench.mode.hotnews.facade.dto.request;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 雷达 AI 主题种子素材落库请求（不经渠道抓取）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiThemeSeedPersistRequest {
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
