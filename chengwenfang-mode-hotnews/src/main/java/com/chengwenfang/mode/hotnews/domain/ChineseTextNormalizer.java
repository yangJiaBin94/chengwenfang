package com.chengwenfang.mode.hotnews.domain;

import cn.hutool.core.util.StrUtil;
import com.github.houbb.opencc4j.util.ZhConverterUtil;

/**
 * 中文文本规范化：繁体 → 简体（OpenCC），供热点标题/摘要入库与对外展示统一用语。
 */
public final class ChineseTextNormalizer {
  private ChineseTextNormalizer() {}

  /**
   * 将文本转为简体中文；空值原样返回。英文与已是简体内容保持不变。
   *
   * @param text 原始文本
   * @return 简体文本
   */
  public static String toSimplified(String text) {
    if (StrUtil.isBlank(text)) {
      return text;
    }
    return ZhConverterUtil.toSimple(text);
  }
}
