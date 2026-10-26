package com.workbench.crawler.facade;

import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从日期输入文本中正则捞疑似日期，并截取前后文供 AI 判定发布日。
 */
public final class PublishDateSnippetSupport {
  /** 命中日期前后各保留字符数。 */
  public static final int CONTEXT_CHARS = 100;
  /** 最多拼接的片段数。 */
  public static final int MAX_SNIPPETS = 12;

  /**
   * 常见日期形态：ISO、斜杠、中文年月日、带时间等。
   */
  private static final Pattern DATE_PATTERN = Pattern.compile(
      "(?:"
          + "20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}(?:日)?"
          + "(?:[T\\s]\\d{1,2}:\\d{2}(?::\\d{2})?)?"
          + "|"
          + "20\\d{2}\\d{2}\\d{2}"
          + ")");

  private PublishDateSnippetSupport() {}

  /**
   * 构造交给 AI 的发布日上下文：有正则命中则拼片段，否则返回受控全文。
   *
   * @param dateInputText 日期输入文本
   * @return AI 用户内容；空入参返回空串
   */
  public static String buildAiContext(String dateInputText) {
    String text = StrUtil.nullToEmpty(dateInputText);
    if (StrUtil.isBlank(text)) {
      return "";
    }
    List<String> snippets = extractSnippets(text);
    if (snippets.isEmpty()) {
      return HtmlDateTextSupport.clipHead(text, HtmlDateTextSupport.DATE_INPUT_MAX_CHARS);
    }
    StringBuilder body = new StringBuilder();
    int index = 1;
    for (String snippet : snippets) {
      body.append("【候选").append(index++).append("】\n").append(snippet).append("\n\n");
    }
    return body.toString().trim();
  }

  /**
   * 提取日期前后文片段（去重）。
   *
   * @param text 全文
   * @return 片段列表
   */
  public static List<String> extractSnippets(String text) {
    List<String> out = new ArrayList<>();
    if (StrUtil.isBlank(text)) {
      return out;
    }
    Set<String> seen = new LinkedHashSet<>();
    Matcher matcher = DATE_PATTERN.matcher(text);
    while (matcher.find() && out.size() < MAX_SNIPPETS) {
      int start = Math.max(0, matcher.start() - CONTEXT_CHARS);
      int end = Math.min(text.length(), matcher.end() + CONTEXT_CHARS);
      String snippet = text.substring(start, end).trim();
      if (StrUtil.isBlank(snippet) || !seen.add(snippet)) {
        continue;
      }
      out.add(snippet);
    }
    return out;
  }
}
