package com.workbench.app.golden.stub;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 加载 {@code classpath:golden/ai-fixtures/} 下由 {@code catalog.json} 索引的规则，并按优先级匹配请求。
 */
public final class AiFixtureCatalog {
  private final List<AiFixtureRule> rules;

  /**
   * @param rules 规则列表（将被复制并排序）
   */
  public AiFixtureCatalog(List<AiFixtureRule> rules) {
    this.rules = new ArrayList<>(rules == null ? List.of() : rules);
    this.rules.sort(
        Comparator.comparingInt(AiFixtureRule::priority).thenComparing(AiFixtureRule::scene,
            Comparator.nullsLast(String::compareTo)));
  }

  /**
   * 从 classpath 按 catalog.json 加载全部场景 fixture。
   *
   * @return 目录
   */
  public static AiFixtureCatalog loadFromClasspath() {
    String catalogRaw = ResourceUtil.readStr("golden/ai-fixtures/catalog.json", StandardCharsets.UTF_8);
    JSONArray files = JSONUtil.parseArray(catalogRaw);
    List<AiFixtureRule> loaded = new ArrayList<>();
    for (Object item : files) {
      String name = StrUtil.trim(StrUtil.nullToEmpty(String.valueOf(item)));
      if (StrUtil.isBlank(name) || !name.endsWith(".json")) {
        continue;
      }
      String raw = ResourceUtil.readStr("golden/ai-fixtures/" + name, StandardCharsets.UTF_8);
      loaded.add(parseRule(raw, name));
    }
    if (loaded.isEmpty()) {
      throw new IllegalStateException("catalog.json 未列出任何 golden/ai-fixtures 规则");
    }
    return new AiFixtureCatalog(loaded);
  }

  /**
   * 解析单条规则 JSON。
   *
   * @param raw      JSON 文本
   * @param fileName 文件名（缺省 scene 时用）
   * @return 规则
   */
  public static AiFixtureRule parseRule(String raw, String fileName) {
    JSONObject json = JSONUtil.parseObj(raw);
    AiFixtureRule rule = new AiFixtureRule();
    String scene = json.getStr("scene");
    if (StrUtil.isBlank(scene) && fileName != null && fileName.endsWith(".json")) {
      scene = fileName.substring(0, fileName.length() - ".json".length());
    }
    rule.setScene(scene);
    rule.setEndpoint(StrUtil.blankToDefault(json.getStr("endpoint"), "chat"));
    rule.setPriority(json.getInt("priority", 100));
    rule.setMatchPath(json.getStr("matchPath"));
    rule.setParser(StrUtil.blankToDefault(json.getStr("parser"), "text"));
    rule.setB64Json(json.getStr("b64Json"));
    JSONArray matchAny = json.getJSONArray("matchAny");
    if (matchAny != null) {
      List<String> needles = new ArrayList<>();
      for (Object needleItem : matchAny) {
        String needle = StrUtil.trim(StrUtil.nullToEmpty(String.valueOf(needleItem)));
        if (StrUtil.isNotBlank(needle)) {
          needles.add(needle);
        }
      }
      rule.setMatchAny(needles);
    }
    Object content = json.get("content");
    if (content instanceof JSONObject || content instanceof JSONArray) {
      rule.setContentJson(JSONUtil.toJsonStr(content));
    } else if (content != null) {
      rule.setContent(String.valueOf(content));
    }
    rule.setForceEmpty(Boolean.TRUE.equals(json.getBool("forceEmpty")));
    if (!rule.forceEmpty()
        && StrUtil.isBlank(rule.assistantText())
        && !"images".equalsIgnoreCase(rule.endpoint())) {
      throw new IllegalStateException("fixture 缺少 content: " + fileName);
    }
    if ("images".equalsIgnoreCase(rule.endpoint()) && StrUtil.isBlank(rule.b64Json())) {
      throw new IllegalStateException("images fixture 缺少 b64Json: " + fileName);
    }
    return rule;
  }

  /**
   * @return 全部规则（已排序）
   */
  public List<AiFixtureRule> rules() {
    return List.copyOf(rules);
  }

  /**
   * 按路径与请求体匹配最高优先级规则；未命中返回 null。
   *
   * @param requestPath URL 路径
   * @param requestBody 请求体；可空
   * @return 命中规则；未命中 null
   */
  public AiFixtureRule match(String requestPath, String requestBody) {
    String path = StrUtil.nullToEmpty(requestPath);
    String body = StrUtil.nullToEmpty(requestBody);
    for (AiFixtureRule rule : rules) {
      if (!pathMatches(rule, path)) {
        continue;
      }
      if ("images".equalsIgnoreCase(rule.endpoint())) {
        return rule;
      }
      if (rule.matchAny().isEmpty()) {
        continue;
      }
      boolean hit = false;
      for (String needle : rule.matchAny()) {
        if (body.contains(needle)) {
          hit = true;
          break;
        }
      }
      if (hit) {
        return rule;
      }
    }
    return null;
  }

  /**
   * 匹配或抛错（黄金桩未命中即失败）。
   *
   * @param requestPath 路径
   * @param requestBody 请求体
   * @return 命中规则
   */
  public AiFixtureRule requireMatch(String requestPath, String requestBody) {
    AiFixtureRule rule = match(requestPath, requestBody);
    if (rule == null) {
      String snippet = StrUtil.nullToEmpty(requestBody);
      if (snippet.length() > 240) {
        snippet = snippet.substring(0, 240) + "...";
      }
      throw new IllegalStateException(
          "AI fixture 未命中任何规则 path=" + requestPath + " bodySnippet=" + snippet);
    }
    return rule;
  }

  private static boolean pathMatches(AiFixtureRule rule, String path) {
    if (StrUtil.isNotBlank(rule.matchPath())) {
      return path.contains(rule.matchPath());
    }
    String endpoint = StrUtil.blankToDefault(rule.endpoint(), "chat").toLowerCase();
    return switch (endpoint) {
      case "images" -> path.contains("/images/generations");
      case "responses" -> path.contains("/responses");
      default -> path.contains("/chat/completions");
    };
  }
}
