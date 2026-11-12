package com.chengwenfang.mode.hotnews.application.generation.hotnews.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 产出的单条佐证截屏请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsEvidenceShotSpec {
  /** 必须来自参考资料列表的 URL。 */
  private String url;
  /** 源页应对准的原文关键词/短句；可空则截首屏。 */
  private String focusHint;
  /** 图下出处文案（不含 URL），如「财联社报道」。 */
  private String caption;
  /** 稿中插入锚点：优先匹配该子串之后插入；可空则跟在文末来源区。 */
  private String insertAfter;
}
