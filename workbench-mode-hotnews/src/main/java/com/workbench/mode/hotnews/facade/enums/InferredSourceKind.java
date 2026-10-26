package com.workbench.mode.hotnews.facade.enums;

/**
 * 选题评分中推断的来源种类（由 URL 主机启发式得到，非渠道配置 type）。
 */
public enum InferredSourceKind {
  /** GitHub。 */
  GITHUB("github"),
  /** arXiv。 */
  ARXIV("arxiv"),
  /** 普通网页。 */
  WEB("web");

  private final String value;

  InferredSourceKind(String value) {
    this.value = value;
  }

  /** @return 写入 score_details 的字面量 */
  public String getValue() {
    return value;
  }
}
