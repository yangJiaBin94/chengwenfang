package com.workbench.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 热点搜索词规范化：把粘贴进来的长描述/关键词簇拆成可检索短词。
 * <p>采集侧、主题问卷、渠道配置共用，避免超长查询拖垮 SearXNG。
 */
public final class SearchQueryNormalizer {
  private SearchQueryNormalizer() {}

  /** 单条查询词最大字符数。 */
  public static final int MAX_QUERY_CHARS = 64;
  /** 单次配置保留的查询词上限。 */
  public static final int MAX_QUERIES = 12;
  /** 过长行按顿号/逗号/分号等拆成原子词。 */
  private static final Pattern ATOM_SPLIT = Pattern.compile("[、，,;；/|＆&]+");

  /**
   * 规范化查询词列表：去空白、拆长串、丢弃仍超长片段、去重并截断条数。
   *
   * @param raw 原始查询词；可为 null
   * @return 可用于搜索的短词列表
   */
  public static List<String> normalize(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    Set<String> unique = new LinkedHashSet<>();
    // 先收已是短词的条目，避免超长粘贴大纲抢先占满 MAX_QUERIES
    for (String item : raw) {
      if (item == null) {
        continue;
      }
      String text = item.trim();
      if (text.isEmpty() || text.length() > MAX_QUERY_CHARS) {
        continue;
      }
      unique.add(text);
      if (unique.size() >= MAX_QUERIES) {
        return new ArrayList<>(unique);
      }
    }
    for (String item : raw) {
      if (item == null) {
        continue;
      }
      String text = item.trim();
      if (text.length() <= MAX_QUERY_CHARS) {
        continue;
      }
      // 长段按分隔符拆开，只保留仍可检索的短词
      for (String atom : ATOM_SPLIT.split(text)) {
        String part = atom == null ? "" : atom.trim();
        if (part.isEmpty() || part.length() > MAX_QUERY_CHARS) {
          continue;
        }
        // 句号结尾的大纲残片去掉句号
        if (part.endsWith("。") || part.endsWith(".")) {
          part = part.substring(0, part.length() - 1).trim();
        }
        if (!part.isEmpty() && part.length() <= MAX_QUERY_CHARS) {
          unique.add(part);
        }
        if (unique.size() >= MAX_QUERIES) {
          return new ArrayList<>(unique);
        }
      }
    }
    return new ArrayList<>(unique);
  }

  /**
   * 从多行文本解析并规范化（前端 textarea / 渠道配置共用语义）。
   *
   * @param text 多行文本
   * @return 规范化后的查询词
   */
  public static List<String> normalizeText(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    List<String> lines = new ArrayList<>();
    for (String line : text.split("\\r?\\n")) {
      if (line != null && !line.isBlank()) {
        lines.add(line.trim());
      }
    }
    return normalize(lines);
  }
}
