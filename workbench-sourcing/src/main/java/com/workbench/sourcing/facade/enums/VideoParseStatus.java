package com.workbench.sourcing.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 资料池视频解析状态。
 */
public enum VideoParseStatus {
  /** 未解析。 */
  NONE("none"),
  /** 解析中。 */
  RUNNING("running"),
  /** 成功。 */
  SUCCEEDED("succeeded"),
  /** 失败。 */
  FAILED("failed");

  private final String value;

  VideoParseStatus(String value) {
    this.value = value;
  }

  /** @return 持久化/API 用小写词 */
  public String getValue() {
    return value;
  }

  /**
   * 解析状态词；无法识别时返回 {@link #NONE}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static VideoParseStatus from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return NONE;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (VideoParseStatus status : values()) {
      if (status.value.equals(key)) {
        return status;
      }
    }
    return NONE;
  }

  /** 是否已成功，可纳入成文参考。 */
  public boolean isSucceeded() {
    return this == SUCCEEDED;
  }

  /**
   * 是否仍可作为批量解析目标（未成功，含卡死的 running / failed / none）。
   *
   * @return 需要解析时为 true
   */
  public boolean needsParse() {
    return this != SUCCEEDED;
  }
}
