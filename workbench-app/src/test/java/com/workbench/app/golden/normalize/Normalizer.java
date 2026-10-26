package com.workbench.app.golden.normalize;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 黄金基线归一化：消除 UUID、时间戳、运行目录、WireMock 端口等不确定片段。
 * <p>同一场景实例内，相同原值映射到同一占位符。
 */
public final class Normalizer {
  /** 标准 UUID（带连字符）。 */
  private static final Pattern UUID_DASHED =
      Pattern.compile(
          "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
  /** 32 位十六进制 ID。 */
  private static final Pattern UUID_HEX32 = Pattern.compile("\\b[0-9a-fA-F]{32}\\b");
  /** ISO-8601 时间（含可选毫秒与时区）。 */
  private static final Pattern ISO_INSTANT =
      Pattern.compile(
          "\\b\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?\\b");
  /** {@code yyyy-MM-dd HH:mm:ss} 与带毫秒变体。 */
  private static final Pattern LOCAL_DATE_TIME =
      Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?\\b");
  /** 耗时字段：`"durationMs":123` / `"firstByteMs": 45`。 */
  private static final Pattern DURATION_FIELD =
      Pattern.compile("(\"(?:durationMs|firstByteMs)\"\\s*:\\s*)\\d+");
  /**
   * 纪元毫秒时间字段（含 AI 请求体里多层转义的 {@code updatedAt\":…} / {@code \"updatedAt\":…}）。
   */
  private static final Pattern EPOCH_MILLIS_FIELD =
      Pattern.compile(
          "((?:createdAt|updatedAt|publishedAt)(?:\\\\)*\"\\s*:\\s*)\\d{10,16}");
  /** WireMock / 本机动态端口：{@code http://127.0.0.1:12345}。 */
  private static final Pattern LOCAL_PORT =
      Pattern.compile("(https?://(?:127\\.0\\.0\\.1|localhost):)\\d+");
  /** 主题实体 ID：{@code theme-<hex>}。 */
  private static final Pattern THEME_ENTITY_ID = Pattern.compile("\\btheme-[0-9a-fA-F]{8,}\\b");
  /** 课程实体 ID（含 code 派生形态）。 */
  private static final Pattern COURSE_ENTITY_ID = Pattern.compile("\\bcourse-[\\w.-]+\\b");
  /** 系列实体 ID。 */
  private static final Pattern SERIES_ENTITY_ID = Pattern.compile("\\bseries-[\\w.-]+\\b");
  /** 主题业务 code：{@code g_course_<8hex>}（课壳场景随机生成）。 */
  private static final Pattern THEME_SHELL_CODE = Pattern.compile("\\bg_course_[0-9a-fA-F]{8}\\b");
  /** 内容版本 SHA-256 十六进制。 */
  private static final Pattern CONTENT_SHA256 = Pattern.compile("\\b[0-9a-fA-F]{64}\\b");
  /** GitHub Search 动态日期限定：{@code updated:>2026-10-18}（含部分 URL 编码形态）。 */
  private static final Pattern GITHUB_UPDATED_QUALIFIER =
      Pattern.compile(
          "updated%3A%3E\\d{4}-\\d{2}-\\d{2}|updated:%3E\\d{4}-\\d{2}-\\d{2}|updated:>\\d{4}-\\d{2}-\\d{2}");
  /** 雷达渠道进度消息中的渠道名。 */
  private static final Pattern RADAR_CHANNEL_LABEL =
      Pattern.compile("「[^」]+」");
  /** 雷达渠道进度：收录/累计候选条数（并行完成顺序抖动）。 */
  private static final Pattern RADAR_CHANNEL_COUNTS =
      Pattern.compile("收录 \\d+ 条 · 累计候选 \\d+ 条");
  /** Hutool multipart 边界随机串。 */
  private static final Pattern HUTOOL_MULTIPART_BOUNDARY =
      Pattern.compile("Hutool_[A-Za-z0-9]+");

  /** 原值 → 占位符。 */
  private final Map<String, String> idPlaceholders = new LinkedHashMap<>();
  /** 运行目录绝对路径前缀（可空）。 */
  private String runtimePrefix = "";

  /**
   * 设置运行目录前缀，归一化时替换为 {@code <RUNTIME>}。
   *
   * @param runtimeDirAbsolute 绝对路径；空则跳过路径替换
   * @return this
   */
  public Normalizer runtimeDir(String runtimeDirAbsolute) {
    this.runtimePrefix = StrUtil.nullToEmpty(runtimeDirAbsolute);
    return this;
  }

  /**
   * 归一化普通文本（AI 请求体、事件消息等）：不重排 JSON 键。
   *
   * @param text 原文；null 当空串
   * @return 归一化后文本
   */
  public String normalizeText(String text) {
    String body = StrUtil.nullToEmpty(text);
    body = replaceIds(body);
    body = ISO_INSTANT.matcher(body).replaceAll("<TS>");
    body = LOCAL_DATE_TIME.matcher(body).replaceAll("<TS>");
    body = EPOCH_MILLIS_FIELD.matcher(body).replaceAll("$1<TS>");
    body = DURATION_FIELD.matcher(body).replaceAll("$1<MS>");
    body = LOCAL_PORT.matcher(body).replaceAll("$1<PORT>");
    body = GITHUB_UPDATED_QUALIFIER.matcher(body).replaceAll("updated:><DATE>");
    body = RADAR_CHANNEL_LABEL.matcher(body).replaceAll("「<CHANNEL>」");
    body = RADAR_CHANNEL_COUNTS.matcher(body).replaceAll("收录 <N> 条 · 累计候选 <N> 条");
    body = HUTOOL_MULTIPART_BOUNDARY.matcher(body).replaceAll("Hutool_<BOUNDARY>");
    if (StrUtil.isNotBlank(runtimePrefix)) {
      body = body.replace(runtimePrefix, "<RUNTIME>");
      String slashVariant = runtimePrefix.replace('\\', '/');
      if (!slashVariant.equals(runtimePrefix)) {
        body = body.replace(slashVariant, "<RUNTIME>");
      }
    }
    return body;
  }

  /**
   * 外部请求 JSONL：先文本归一化，再按行排序（并行渠道发出顺序不确定）。
   *
   * @param jsonl 外部录制原文
   * @return 稳定文本
   */
  public String normalizeExternalJsonl(String jsonl) {
    String body = normalizeText(jsonl);
    if (StrUtil.isBlank(body)) {
      return body;
    }
    boolean endsWithNewline = body.endsWith("\n") || body.endsWith("\r");
    List<String> lines = new ArrayList<>();
    for (String line : body.split("\\R")) {
      if (StrUtil.isNotEmpty(line)) {
        lines.add(line);
      }
    }
    lines.sort(String::compareTo);
    String joined = String.join("\n", lines);
    return endsWithNewline ? joined + "\n" : joined;
  }

  /**
   * 归一化业务产物 JSON：先文本归一化，再对对象键递归排序后输出。
   *
   * @param jsonText JSON 文本；非法 JSON 时仅做文本归一化
   * @return 归一化后的 JSON 文本
   */
  public String normalizeOutputsJson(String jsonText) {
    String normalized = normalizeText(jsonText);
    if (StrUtil.isBlank(normalized) || !JSONUtil.isTypeJSON(normalized)) {
      return normalized;
    }
    Object parsed = JSONUtil.parse(normalized);
    Object sorted = sortKeys(parsed);
    return JSONUtil.toJsonPrettyStr(sorted);
  }

  /**
   * 替换 UUID / 32 位十六进制为稳定占位符。
   *
   * @param body 原文
   * @return 替换后文本
   */
  private String replaceIds(String body) {
    String afterDashed = replaceWithStableIds(body, UUID_DASHED);
    String afterHex = replaceWithStableIds(afterDashed, UUID_HEX32);
    String afterSha = replaceWithStableIds(afterHex, CONTENT_SHA256);
    String afterTheme = replaceWithStableIds(afterSha, THEME_ENTITY_ID);
    String afterCourse = replaceWithStableIds(afterTheme, COURSE_ENTITY_ID);
    String afterSeries = replaceWithStableIds(afterCourse, SERIES_ENTITY_ID);
    return replaceWithStableIds(afterSeries, THEME_SHELL_CODE);
  }

  /**
   * 按首次出现顺序分配 {@code <ID-n>}。
   *
   * @param body    原文
   * @param pattern ID 模式
   * @return 替换后文本
   */
  private String replaceWithStableIds(String body, Pattern pattern) {
    Matcher matcher = pattern.matcher(body);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String raw = matcher.group();
      String key = raw.toLowerCase();
      String placeholder =
          idPlaceholders.computeIfAbsent(key, ignored -> "<ID-" + (idPlaceholders.size() + 1) + ">");
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(placeholder));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  /**
   * 递归按键名排序 JSON 对象；数组保持元素顺序。
   *
   * @param node Hutool JSON 节点或标量
   * @return 排序后的结构
   */
  private static Object sortKeys(Object node) {
    if (node instanceof JSONObject object) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      for (String key : object.keySet()) {
        sorted.put(key, sortKeys(object.get(key)));
      }
      return sorted;
    }
    if (node instanceof JSONArray array) {
      List<Object> list = new ArrayList<>(array.size());
      for (Object element : array) {
        list.add(sortKeys(element));
      }
      return list;
    }
    if (node instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        sorted.put(String.valueOf(entry.getKey()), sortKeys(entry.getValue()));
      }
      return sorted;
    }
    if (node instanceof List<?> list) {
      List<Object> out = new ArrayList<>(list.size());
      for (Object element : list) {
        out.add(sortKeys(element));
      }
      return out;
    }
    return node;
  }
}
