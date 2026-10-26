package com.workbench.mode.course.application.course;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课程 Markdown 拆分辅助（由 CourseMarkdownUtil 门面委派）。
 */
public final class CourseMarkdownFormulas {
  /** 工具类不可实例化。 */
  private CourseMarkdownFormulas() {}

  /**
   * 统计课程「正文字数」：去掉提示词块、全部代码/提示词围栏、图片与链接语法、占位符、
   * 标题前缀与强调符后的非空白字符数。
   *
   * @param content Markdown 全文；null 视为空
   * @return 正文字符数
   */
  public static int lessonBodyCharCount(String content) {
    // 规范「插图位置 + 围栏」整块不计入
    String cleaned = CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(content == null ? "" : content).replaceAll("\n");
    // python / bash / text / 无语言标记等围栏一律不计入
    cleaned = CourseMarkdownUtil.FENCED_BLOCK_JAVA.matcher(cleaned).replaceAll("\n");
    cleaned = cleaned.replaceAll("!\\[[^]]*]\\([^)]*\\)", "");
    // 链接只留可见文案，去掉 URL
    cleaned = cleaned.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
    cleaned = cleaned.replaceAll("\\[\\[COURSE_(?:COVER|IMAGE_\\d+)]]", "");
    cleaned = cleaned.replaceAll("(?m)^\\s*(?:#{1,6}\\s+|>\\s+)", "");
    // 行内代码与强调符
    cleaned = cleaned.replaceAll("[*_`~]", "");
    return cleaned.replaceAll("\\s+", "").length();
  }
  /**
   * 判断单块公众号转换是否合格：保留率 ∈ [75%, 135%]，且源中图片 token 均保留。
   */
  public static boolean wechatChunkComplete(String source, String converted) {
    if (converted == null || converted.isBlank()) {
      return false;
    }
    double ratio = converted.strip().length() / (double) Math.max(1, source.strip().length());
    if (ratio < CourseMarkdownUtil.WECHAT_CHUNK_MIN_PRESERVATION || ratio > CourseMarkdownUtil.WECHAT_CHUNK_MAX_EXPANSION) {
      return false;
    }
    Matcher tokenMatcher = Pattern.compile("\\[\\[COURSE_IMAGE_\\d+]]").matcher(source);
    while (tokenMatcher.find()) {
      if (!converted.contains(tokenMatcher.group())) {
        return false;
      }
    }
    return true;
  }
  /**
   * 将字数档位解析为正文上限。
   *
   * @param wordCountOption 档位键，如 {@code 2000-4000}
   * @return 上限字符数
   */
  public static int maxBodyChars(String wordCountOption) {
    return switch (wordCountOption == null ? "" : wordCountOption) {
      case "under-2000" -> 2000;
      case "2000-4000" -> 4000;
      case "4000-6000" -> 6000;
      case "6000-8000" -> 8000;
      default -> CourseMarkdownUtil.DEFAULT_MAX_LESSON_BODY_CHARS;
    };
  }
  /**
   * 将字数档位解析为正文下限；未识别档位为 0。
   *
   * @param wordCountOption 档位键
   * @return 下限字符数
   */
  public static int minBodyChars(String wordCountOption) {
    return switch (wordCountOption == null ? "" : wordCountOption) {
      case "2000-4000" -> 2000;
      case "4000-6000" -> 4000;
      case "6000-8000" -> 6000;
      default -> 0;
    };
  }
  /**
   * 压缩目标字数：在上限下留安全余量，并按超标比例收紧。
   *
   * @param bodyChars    当前正文字数
   * @param maxBodyChars 正文上限
   * @return 本轮压缩目标（至少 1）
   */
  public static int compressionTarget(int bodyChars, int maxBodyChars) {
    int safetyTarget = maxBodyChars - Math.max(180, Math.round(maxBodyChars * 0.05f));
    int proportionalTarget = Math.round(
        safetyTarget * Math.min(1f, maxBodyChars / (float) Math.max(1, bodyChars)));
    return Math.max(1, Math.min(safetyTarget, proportionalTarget));
  }
  /** 非重叠子串计数。 */
  public static long countOccurrences(String text, String needle) {
    if (text == null || needle == null || needle.isEmpty()) {
      return 0;
    }
    int count = 0;
    int from = 0;
    while (true) {
      int idx = text.indexOf(needle, from);
      if (idx < 0) {
        return count;
      }
      count++;
      from = idx + needle.length();
    }
  }
}
