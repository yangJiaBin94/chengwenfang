package com.chengwenfang.crawler.facade;

import cn.hutool.core.util.StrUtil;
import java.util.regex.Pattern;

/**
 * 将原始 HTML 转为适合抽取发布时间的纯文本：去掉 style，保留 script 内文本，再剥标签。
 */
public final class HtmlDateTextSupport {
  /** 日期输入硬顶字符数（优先保留头部）。 */
  public static final int DATE_INPUT_MAX_CHARS = 12_000;

  private static final Pattern STYLE_BLOCK = Pattern.compile(
      "(?is)<style\\b[^>]*>.*?</style>");
  private static final Pattern SCRIPT_OPEN = Pattern.compile("(?is)<script\\b[^>]*>");
  private static final Pattern SCRIPT_CLOSE = Pattern.compile("(?is)</script>");
  private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private HtmlDateTextSupport() {}

  /**
   * 去标签得到日期抽取输入文本。
   *
   * @param html 原始 HTML；可空
   * @return 压缩后的纯文本；空入参返回空串
   */
  public static String toDateInputText(String html) {
    if (StrUtil.isBlank(html)) {
      return "";
    }
    String text = STYLE_BLOCK.matcher(html).replaceAll(" ");
    // 去掉 script 外壳，保留中间 JSON-LD / pubDate 等文本
    text = SCRIPT_OPEN.matcher(text).replaceAll(" ");
    text = SCRIPT_CLOSE.matcher(text).replaceAll(" ");
    text = HTML_TAG.matcher(text).replaceAll(" ");
    text = WHITESPACE.matcher(text).replaceAll(" ").trim();
    return clipHead(text, DATE_INPUT_MAX_CHARS);
  }

  /**
   * 截断过长文本，优先保留头部。
   *
   * @param text 文本
   * @param max  最大长度
   * @return 截断结果
   */
  public static String clipHead(String text, int max) {
    String value = StrUtil.nullToEmpty(text);
    if (max <= 0 || value.length() <= max) {
      return value;
    }
    return value.substring(0, max);
  }
}
