package com.workbench.sourcing.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 雷达信源媒体类型：文章或视频。
 */
public enum SourceMediaType {
  /** 图文/网页文章。 */
  ARTICLE("article"),
  /** 视频平台或视频页。 */
  VIDEO("video");

  private final String value;

  SourceMediaType(String value) {
    this.value = value;
  }

  /** @return 持久化小写词 */
  public String getValue() {
    return value;
  }

  /**
   * 解析媒体类型；无法识别时返回 {@link #ARTICLE}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static SourceMediaType from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return ARTICLE;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (SourceMediaType type : values()) {
      if (type.value.equals(key)) {
        return type;
      }
    }
    return ARTICLE;
  }

  /** 是否视频。 */
  public boolean isVideo() {
    return this == VIDEO;
  }
}
