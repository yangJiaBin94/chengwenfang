package com.chengwenfang.mode.hotnews.domain;

import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从已抽取的源页正文中，按佐证线索选出可交给 Playwright 定位的短锚点。
 */
public final class HotNewsEvidenceAnchorSupport {
  /** 数字/百分号等硬锚点。 */
  private static final Pattern NUMBER_TOKEN = Pattern.compile(
      "\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?%?"
          + "|\\d+(?:\\.\\d+)?%"
          + "|\\d+(?:\\.\\d+)?万?"
          + "|\\d{4,}");

  /** 分句。 */
  private static final Pattern SENTENCE_SPLIT = Pattern.compile("[。！？!?\\n]+");

  /** 线索再切分（AI 常把多事实用分号粘在一起）。 */
  private static final Pattern HINT_SPLIT = Pattern.compile("[；;。！？!?\\n]+");

  /** 普通定位锚点最短长度。 */
  private static final int MIN_ANCHOR_CHARS = 6;

  /** 数字类硬锚点最短长度（如 6.6万、12%）。 */
  private static final int MIN_NUMBER_ANCHOR_CHARS = 3;

  /** 定位锚点最长长度（过长易因 DOM 差异失败）。 */
  private static final int MAX_ANCHOR_CHARS = 40;

  private HotNewsEvidenceAnchorSupport() {}

  /**
   * 判断 URL 是否应按 PDF 首页截屏（不走网页正文定位）。
   *
   * @param url 目标地址
   * @return PDF 为 true
   */
  public static boolean looksLikePdfUrl(String url) {
    String lower = StrUtil.nullToEmpty(url).trim().toLowerCase(Locale.ROOT);
    return lower.contains(".pdf") || lower.endsWith("/pdf");
  }

  /**
   * 在源页正文中为 focusHint 选出短定位句。
   *
   * @param sourceBody 已抽取正文（须来自真实网页，非搜索摘要）
   * @param focusHint  AI 给出的佐证线索（可为多事实拼句）
   * @return 适合 {@code --find-text} 的短锚点；找不到返回 null
   */
  public static String resolveFindText(String sourceBody, String focusHint) {
    String body = normalizeForMatch(sourceBody);
    if (StrUtil.isBlank(body)) {
      return null;
    }
    List<String> queries = buildQueryPieces(focusHint);
    if (queries.isEmpty()) {
      return null;
    }
    String bestSentence = null;
    int bestScore = 0;
    for (String sentence : splitSentences(body)) {
      int score = scoreSentence(sentence, queries);
      if (score > bestScore) {
        bestScore = score;
        bestSentence = sentence;
      }
    }
    if (bestScore <= 0 || StrUtil.isBlank(bestSentence)) {
      // 正文里直接命中某个短线索时，退回该线索本身
      for (String query : queries) {
        if (query.length() >= MIN_ANCHOR_CHARS && body.contains(query)) {
          return trimAnchor(query);
        }
      }
      return null;
    }
    return pickAnchorFromSentence(bestSentence, queries);
  }

  /**
   * 规范化匹配用文本：弯引号、空白。
   *
   * @param text 原文
   * @return 规范化文本
   */
  static String normalizeForMatch(String text) {
    if (text == null) {
      return "";
    }
    String normalized = text
        .replace('\u201c', '"')
        .replace('\u201d', '"')
        .replace('\u2018', '\'')
        .replace('\u2019', '\'')
        .replace('\u3000', ' ');
    normalized = normalized.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
    return normalized.trim();
  }

  private static List<String> buildQueryPieces(String focusHint) {
    Set<String> pieces = new LinkedHashSet<>();
    String hint = normalizeForMatch(focusHint);
    if (StrUtil.isBlank(hint)) {
      return List.of();
    }
    for (String part : HINT_SPLIT.split(hint)) {
      String trimmed = StrUtil.trim(part);
      if (StrUtil.isBlank(trimmed)) {
        continue;
      }
      pieces.add(trimmed);
      Matcher matcher = NUMBER_TOKEN.matcher(trimmed);
      while (matcher.find()) {
        pieces.add(matcher.group());
      }
    }
    Matcher wholeNumbers = NUMBER_TOKEN.matcher(hint);
    while (wholeNumbers.find()) {
      pieces.add(wholeNumbers.group());
    }
    List<String> ordered = new ArrayList<>(pieces);
    ordered.sort((left, right) -> Integer.compare(right.length(), left.length()));
    return ordered;
  }

  private static List<String> splitSentences(String body) {
    List<String> sentences = new ArrayList<>();
    for (String part : SENTENCE_SPLIT.split(body)) {
      String sentence = StrUtil.trim(part);
      if (sentence.length() >= MIN_ANCHOR_CHARS) {
        sentences.add(sentence);
      }
    }
    if (sentences.isEmpty() && body.length() >= MIN_ANCHOR_CHARS) {
      sentences.add(body);
    }
    return sentences;
  }

  private static int scoreSentence(String sentence, List<String> queries) {
    int score = 0;
    for (String query : queries) {
      if (sentence.contains(query)) {
        // 数字线索加权更高
        if (NUMBER_TOKEN.matcher(query).matches()) {
          score += 30 + query.length();
        } else {
          score += Math.min(40, query.length());
        }
      }
    }
    return score;
  }

  private static String pickAnchorFromSentence(String sentence, List<String> queries) {
    // 优先：句中命中的数字硬锚点
    for (String query : queries) {
      if (isNumberToken(query)
          && query.length() >= MIN_NUMBER_ANCHOR_CHARS
          && query.length() <= MAX_ANCHOR_CHARS
          && sentence.contains(query)) {
        return query;
      }
    }
    // 其次：句中完整命中的短线索
    for (String query : queries) {
      if (query.length() >= MIN_ANCHOR_CHARS
          && query.length() <= MAX_ANCHOR_CHARS
          && sentence.contains(query)) {
        return query;
      }
    }
    for (String query : queries) {
      int index = sentence.indexOf(query);
      if (index < 0 || query.length() < 2) {
        continue;
      }
      int start = Math.max(0, index - 4);
      int end = Math.min(sentence.length(), index + query.length() + 12);
      String window = sentence.substring(start, end).trim();
      if (window.length() >= MIN_ANCHOR_CHARS) {
        return trimAnchor(window);
      }
    }
    return trimAnchor(sentence);
  }

  private static boolean isNumberToken(String text) {
    return NUMBER_TOKEN.matcher(StrUtil.nullToEmpty(text)).matches();
  }

  private static String trimAnchor(String text) {
    String anchor = StrUtil.trim(text);
    if (anchor.length() <= MAX_ANCHOR_CHARS) {
      return anchor;
    }
    return anchor.substring(0, MAX_ANCHOR_CHARS).trim();
  }
}
