package com.workbench.crawler.application.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trafilatura CLI 一行 JSON 结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafilaturaCliResult {
  /** 是否成功。 */
  private boolean ok;
  /** 正文。 */
  private String text;
  /** ISO 日期或空。 */
  private String date;
  /** 去标签后的日期抽取输入（保留 script 文本）。 */
  private String dateInputText;
  /** 错误信息。 */
  private String error;
}
