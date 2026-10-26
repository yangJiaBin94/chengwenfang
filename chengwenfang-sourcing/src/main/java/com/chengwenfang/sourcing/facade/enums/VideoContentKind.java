package com.chengwenfang.sourcing.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 视频资料内容类型：决定走语音链路还是画面链路。
 */
public enum VideoContentKind {
  /** 可字幕/口播解析。 */
  SPEECH("speech"),
  /** 非语音可处理，仅抽帧 + 多模态。 */
  VISUAL("visual");

  private final String value;

  VideoContentKind(String value) {
    this.value = value;
  }

  /** @return 持久化字符串 */
  public String getValue() {
    return value;
  }

  /**
   * 是否为画面类型（含空白以外的 visual）。
   *
   * @param raw 库中或请求中的原始值
   * @return true 表示应跳过字幕/ASR
   */
  public static boolean isVisual(String raw) {
    return from(raw) == VISUAL;
  }

  /**
   * 解析类型；空白或无法识别时视为 {@link #SPEECH}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static VideoContentKind from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return SPEECH;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (VideoContentKind kind : values()) {
      if (kind.value.equals(key)) {
        return kind;
      }
    }
    return SPEECH;
  }
}
