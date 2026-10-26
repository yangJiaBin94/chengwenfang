package com.chengwenfang.sourcing.application;

import cn.hutool.core.util.StrUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 字幕 / ASR 文本是否可作为有效口播的质量门。
 */
public final class VideoSpeechTextQuality {
  /** 去空白后最短有效长度。 */
  public static final int MIN_LENGTH = 30;
  /** 有效汉字或字母数字占比下限。 */
  public static final double MIN_MEANINGFUL_RATIO = 0.35;
  /** 单一字符重复占比上限（如大段「嘿」）。 */
  public static final double MAX_SINGLE_CHAR_RATIO = 0.55;

  private static final Pattern MEANINGFUL = Pattern.compile("[\\p{IsHan}A-Za-z0-9]");

  private VideoSpeechTextQuality() {}

  /**
   * 判断文本是否像有效口播/字幕。
   *
   * @param text 原始文本
   * @return true 表示可用于文本总结
   */
  public static boolean isValidSpeechText(String text) {
    String normalized = StrUtil.nullToEmpty(text).replaceAll("\\s+", "").trim();
    if (normalized.length() < MIN_LENGTH) {
      return false;
    }
    int meaningful = 0;
    Map<Integer, Integer> codePointCounts = new HashMap<>();
    for (int index = 0; index < normalized.length(); ) {
      int codePoint = normalized.codePointAt(index);
      String piece = new String(Character.toChars(codePoint));
      if (MEANINGFUL.matcher(piece).matches()) {
        meaningful++;
      }
      codePointCounts.merge(codePoint, 1, Integer::sum);
      index += Character.charCount(codePoint);
    }
    double meaningfulRatio = (double) meaningful / normalized.length();
    if (meaningfulRatio < MIN_MEANINGFUL_RATIO) {
      return false;
    }
    int maxCount = codePointCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    return (double) maxCount / normalized.length() <= MAX_SINGLE_CHAR_RATIO;
  }
}
