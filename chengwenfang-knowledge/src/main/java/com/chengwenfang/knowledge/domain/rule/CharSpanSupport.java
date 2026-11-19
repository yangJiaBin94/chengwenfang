package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;

/**
 * 归一化正文上的字符区间辅助：保证 content 与 text[start,end) 一致。
 */
public final class CharSpanSupport {

  private CharSpanSupport() {}

  /**
   * 去掉首尾空白后在原文中的精确区间（相对 baseOffset）。
   *
   * @param raw        可能含首尾空白的原文片段
   * @param baseOffset 该片段在全文中的起点
   * @return [startInclusive, endExclusive]；空白串返回 [base, base]
   */
  public static int[] spanOfTrimmed(String raw, int baseOffset) {
    String text = raw == null ? "" : raw;
    int start = 0;
    int end = text.length();
    while (start < end && Character.isWhitespace(text.charAt(start))) {
      start++;
    }
    while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    return new int[] {baseOffset + start, baseOffset + end};
  }

  /**
   * 在 source 的 [from, source.length) 内定位 needle 的首次出现。
   *
   * @param source 全文
   * @param needle 子串
   * @param from   起始搜索位置
   * @return [start, end]；找不到则 [from, from]
   */
  public static int[] spanOf(String source, String needle, int from) {
    if (StrUtil.isBlank(needle) || source == null) {
      int safeFrom = Math.max(0, from);
      return new int[] {safeFrom, safeFrom};
    }
    int start = source.indexOf(needle, Math.max(0, from));
    if (start < 0) {
      int safeFrom = Math.max(0, from);
      return new int[] {safeFrom, safeFrom};
    }
    return new int[] {start, start + needle.length()};
  }
}
