package com.workbench.mode.hotnews.domain;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 选题资料补充：从嘈杂标题提取检索主体，并生成多路检索词。
 */
public final class TopicResearchPlanner {
  /** 据某某报道： */
  private static final Pattern REPORTED_BY = Pattern.compile(
      "^据[^：:]{1,20}报道\\s*[：:]\\s*");
  /** 栏目后缀：| 每日AI午报 … */
  private static final Pattern PIPE_TAIL = Pattern.compile(
      "\\s*[|｜].*$");
  /** 站点尾巴：-36氪 / —IT之家 */
  private static final Pattern SITE_TAIL = Pattern.compile(
      "\\s*[-—–]\\s*[\\u4e00-\\u9fffA-Za-z0-9]{1,20}$");
  /** 英文产品名片段，如 Reflection AI / GPT-5 */
  private static final Pattern LATIN_ENTITY = Pattern.compile(
      "\\b([A-Z][A-Za-z0-9]+(?:\\s+[A-Z][A-Za-z0-9./+-]+){0,4})\\b");

  private TopicResearchPlanner() {}

  /**
   * 从选题标题提取检索主体（去掉「据…报道」「午报栏目」等噪音）。
   *
   * @param title 选题标题
   * @return 主体短语；无法解析时返回 trim 后的原标题
   */
  public static String extractSubject(String title) {
    if (title == null || title.isBlank()) {
      return "";
    }
    String text = title.trim();
    text = REPORTED_BY.matcher(text).replaceFirst("");
    text = PIPE_TAIL.matcher(text).replaceFirst("");
    text = SITE_TAIL.matcher(text).replaceFirst("");
    text = text.replaceAll("\\s+", " ").trim();
    return text.isBlank() ? title.trim() : text;
  }

  /**
   * 从主体中再抽短关键词（优先拉丁产品名 + 关键中文专名）。
   *
   * @param subject {@link #extractSubject(String)} 结果
   * @return 短查询；无额外短词时返回 subject 本身
   */
  public static String extractShortQuery(String subject) {
    if (subject == null || subject.isBlank()) {
      return "";
    }
    Matcher matcher = LATIN_ENTITY.matcher(subject);
    List<String> latin = new ArrayList<>();
    while (matcher.find() && latin.size() < 3) {
      String hit = matcher.group(1).trim();
      if (hit.length() >= 2 && !latin.contains(hit)) {
        latin.add(hit);
      }
    }
    if (!latin.isEmpty()) {
      return String.join(" ", latin);
    }
    return subject.length() > 48 ? subject.substring(0, 48).trim() : subject;
  }

  /**
   * 构造资料补充检索词：官网/声明/分析/开源等多角度。
   *
   * @param topicTitle 选题标题
   * @param summary    选题摘要，可空
   * @return 去重后的检索词（有序）
   */
  public static List<String> buildQueries(String topicTitle, String summary) {
    String subject = extractSubject(topicTitle);
    String shortQuery = extractShortQuery(subject);
    List<String> seeds = new ArrayList<>();
    if (!shortQuery.isBlank()) {
      seeds.add(shortQuery);
    }
    if (!subject.isBlank() && !subject.equals(shortQuery)) {
      seeds.add(subject);
    }
    if (summary != null && !summary.isBlank()) {
      String hint = extractSubject(summary);
      if (hint.length() >= 8 && hint.length() <= 60) {
        seeds.add(hint);
      }
    }
    return buildQueriesFromPlan(subject, seeds, null);
  }

  /**
   * 基于 AI/规则检索计划展开多角度查询词。
   *
   * @param subject         主体
   * @param keywords        关键词种子
   * @param disambiguation  消歧说明（可空；不直接进搜索串）
   * @return 去重后的查询列表
   */
  public static List<String> buildQueriesFromPlan(
      String subject, List<String> keywords, String disambiguation) {
    String primary = subject == null ? "" : subject.trim();
    if (primary.isBlank() && keywords != null && !keywords.isEmpty()) {
      primary = keywords.get(0) == null ? "" : keywords.get(0).trim();
    }
    if (primary.isBlank()) {
      return List.of("AI news official announcement");
    }
    Set<String> queries = new LinkedHashSet<>();
    if (keywords != null) {
      for (String kw : keywords) {
        if (kw != null && !kw.isBlank()) {
          queries.add(kw.trim());
        }
      }
    }
    queries.add(primary);
    queries.add(primary + " official");
    queries.add(primary + " 官网");
    queries.add(primary + " announcement");
    queries.add(primary + " 官方 声明");
    queries.add(primary + " GitHub");
    queries.add(primary + " documentation");
    queries.add(primary + " analysis");
    // disambiguation 仅用于核验上下文，不拼进检索词
    return queries.stream().filter(q -> q != null && !q.isBlank()).limit(8).toList();
  }

  /**
   * 规范化 URL 供去重（去 fragment、尾斜杠、常见追踪参数）。
   *
   * @param url 原始链接
   * @return 规范化键；无法解析时返回 trim 小写原串
   */
  public static String normalizeUrl(String url) {
    if (url == null || url.isBlank()) {
      return "";
    }
    String raw = url.trim();
    try {
      URI uri = URI.create(raw);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
      String path = uri.getPath() == null ? "" : uri.getPath();
      if (path.endsWith("/") && path.length() > 1) {
        path = path.substring(0, path.length() - 1);
      }
      String query = uri.getQuery();
      if (query != null && !query.isBlank()) {
        List<String> kept = new ArrayList<>();
        for (String part : query.split("&")) {
          String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
          if (key.startsWith("utm_") || key.equals("fbclid") || key.equals("gclid")) {
            continue;
          }
          kept.add(part);
        }
        query = kept.isEmpty() ? null : String.join("&", kept);
      }
      StringBuilder out = new StringBuilder();
      out.append(scheme).append("://").append(host).append(path);
      if (query != null) {
        out.append('?').append(query);
      }
      return out.toString();
    } catch (Exception ignored) {
      return raw.toLowerCase(Locale.ROOT);
    }
  }

  /**
   * 选题素材去重键：优先规范化 URL，无 URL 时用小写标题。
   *
   * @param url   素材链接；可空
   * @param title 素材标题；可空
   * @return {@code url:} / {@code title:} 前缀键；URL 与标题皆空时返回 {@code title:}
   */
  public static String materialDedupeKey(String url, String title) {
    String urlKey = normalizeUrl(url);
    if (!urlKey.isBlank()) {
      return "url:" + urlKey;
    }
    String titleKey = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
    return "title:" + titleKey;
  }

  /**
   * 是否为可参与排除匹配的去重键（排除空标题键）。
   *
   * @param key {@link #materialDedupeKey(String, String)} 结果
   * @return 有实质内容时 true
   */
  public static boolean isMeaningfulMaterialDedupeKey(String key) {
    if (key == null || key.isBlank()) {
      return false;
    }
    return !"title:".equals(key) && !"url:".equals(key);
  }
}
