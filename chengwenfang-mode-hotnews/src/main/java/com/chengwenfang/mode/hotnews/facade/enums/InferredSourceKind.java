package com.chengwenfang.mode.hotnews.facade.enums;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.facade.constant.PreprintSourceHosts;
import java.util.Locale;

/**
 * 选题评分中推断的来源种类（由 URL 主机启发式得到，非渠道配置 type）。
 */
public enum InferredSourceKind {
  /** GitHub。 */
  GITHUB("github"),
  /** arXiv（预印本族中的 arXiv 主机，保留兼容字面量）。 */
  ARXIV("arxiv"),
  /** 其它预印本 / 论文库（bioRxiv、OpenReview 等）。 */
  PREPRINT("preprint"),
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

  /**
   * 由主机名推断来源种类。
   *
   * @param host URL 主机；可空
   * @return 推断结果；无法识别时为 {@link #WEB}
   */
  public static InferredSourceKind fromHost(String host) {
    if (StrUtil.isBlank(host)) {
      return WEB;
    }
    String normalized = host.trim().toLowerCase(Locale.ROOT);
    if (normalized.contains(GITHUB.getValue())) {
      return GITHUB;
    }
    if (PreprintSourceHosts.hostMatches(normalized)) {
      return normalized.contains(ARXIV.getValue()) ? ARXIV : PREPRINT;
    }
    return WEB;
  }

  /**
   * 是否属于预印本 / 论文库族（含 arXiv）。
   *
   * @return true 表示单源时不应主推
   */
  public boolean isPreprintFamily() {
    return this == ARXIV || this == PREPRINT;
  }
}
