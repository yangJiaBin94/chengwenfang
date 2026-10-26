package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 热点正文字数档解析与校验（按去掉空白后的字符数近似字数）。
 * <p>插图提示词块不计入字数。
 */
public final class HotNewsWordCountRules {
  private HotNewsWordCountRules() {}

  /** 默认允许的浮动比例（相对档位上下限）。 */
  public static final double DEFAULT_TOLERANCE = 0.15;

  /**
   * 字数区间。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Range {
    /** 下限（含）。 */
    private final int minInclusive;
    /** 上限（含）。 */
    private final int maxInclusive;
  }

  /**
   * 解析字数档，如 {@code 2500-4000}、{@code 4000+}。
   *
   * @param option 档位字面量
   * @return 区间；无法解析则 empty 语义用 0..MAX
   */
  public static Range parse(String option) {
    if (StrUtil.isBlank(option)) {
      return new Range(0, Integer.MAX_VALUE);
    }
    String normalized = option.trim();
    if (normalized.endsWith("+")) {
      int min = parseIntOrDefault(normalized.substring(0, normalized.length() - 1), 0);
      return new Range(min, Integer.MAX_VALUE);
    }
    int dash = normalized.indexOf('-');
    if (dash > 0) {
      int min = parseIntOrDefault(normalized.substring(0, dash), 0);
      int max = parseIntOrDefault(normalized.substring(dash + 1), Integer.MAX_VALUE);
      if (max < min) {
        max = min;
      }
      return new Range(min, max);
    }
    int exact = parseIntOrDefault(normalized, 0);
    return new Range(exact, exact);
  }

  /**
   * 统计正文近似字数：去掉插图提示词块、图片语法与 Markdown 装饰后的字符数。
   *
   * @param markdown 正文
   * @return 字数（不含插图提示词）
   */
  public static int countChars(String markdown) {
    return HotNewsMarkdownUtil.countBodyChars(markdown);
  }

  /**
   * 是否在档位允许范围内（含浮动）。
   *
   * @param markdown  正文
   * @param option    字数档
   * @param tolerance 浮动比例，如 0.15
   * @return 合规则为 true
   */
  public static boolean withinRange(String markdown, String option, double tolerance) {
    Range range = parse(option);
    int count = countChars(markdown);
    double ratio = Math.max(0, tolerance);
    int min = (int) Math.floor(range.minInclusive() * (1 - ratio));
    int max = range.maxInclusive() == Integer.MAX_VALUE
        ? Integer.MAX_VALUE
        : (int) Math.ceil(range.maxInclusive() * (1 + ratio));
    return count >= min && count <= max;
  }

  /**
   * 生成不合规说明。
   *
   * @param markdown 正文
   * @param option   字数档
   * @return 说明文案
   */
  public static String describeMismatch(String markdown, String option) {
    Range range = parse(option);
    int count = countChars(markdown);
    String upper = range.maxInclusive() == Integer.MAX_VALUE
        ? "以上"
        : String.valueOf(range.maxInclusive());
    return "字数 " + count + " 不在档位 " + range.minInclusive() + "-" + upper
        + "（允许 ±" + Math.round(DEFAULT_TOLERANCE * 100) + "%）";
  }

  private static int parseIntOrDefault(String raw, int defaultValue) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ignored) {
      return defaultValue;
    }
  }
}
