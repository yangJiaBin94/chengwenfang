package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 写入 prompt 的参考资料一行。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsReferenceLine {
  /** 标题。 */
  private String title;
  /** 摘要（截断后）。 */
  private String summary;
  /** 链接。 */
  private String url;
}
