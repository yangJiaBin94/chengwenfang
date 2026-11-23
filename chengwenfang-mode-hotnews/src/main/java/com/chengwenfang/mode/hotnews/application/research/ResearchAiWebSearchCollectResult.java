package com.chengwenfang.mode.hotnews.application.research;

import com.chengwenfang.crawler.facade.dto.view.SearchHitView;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料补充 AI 联网搜采集结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchAiWebSearchCollectResult {
  /** 是否实际发起了联网搜索。 */
  private boolean used;
  /** 跳过或失败原因；成功时可空。 */
  private String skipReason;
  /** 引用链接数。 */
  private int citationCount;
  /** 闸门通过后的命中。 */
  @Builder.Default
  private List<SearchHitView> hits = new ArrayList<>();
}
