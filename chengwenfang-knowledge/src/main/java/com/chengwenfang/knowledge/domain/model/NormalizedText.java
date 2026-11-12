package com.chengwenfang.knowledge.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 抽取后的归一化纯文本。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedText {
  /** 正文。 */
  private String text;
  /** 标题（可与文档标题一致或从正文推断）。 */
  private String title;
}
