package com.workbench.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import com.workbench.crawler.facade.enums.ChannelType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Service;

/**
 * 渠道建议解析：模型 JSON → 候选与抓取配置。
 */
@Service
public class ChannelSuggestionParser {

  static List<AiChannelCandidate> parseCandidates(String text) {
    JSONObject json = extractJsonObject(text);
    if (json == null) {
      return List.of();
    }
    JSONArray array = json.getJSONArray("channels");
    if (array == null || array.isEmpty()) {
      return List.of();
    }
    List<AiChannelCandidate> out = new ArrayList<>();
    for (int index = 0;
        index < array.size() && out.size() < HotNewsThemeSourceChannelGenerateService.MAX_CHANNELS;
        index++) {
      JSONObject item = array.getJSONObject(index);
      if (item == null) {
        continue;
      }
      String name = StrUtil.trim(StrUtil.nullToEmpty(item.getStr("name")));
      String type = StrUtil.trim(StrUtil.nullToEmpty(item.getStr("type")));
      String baseUrl = StrUtil.trim(StrUtil.nullToEmpty(item.getStr("baseUrl")));
      ChannelFetchConfig config = parseConfig(item.get("config"));
      // 网页 urls：解析阶段即去重并截断
      if (ChannelType.WEB.getValue().equalsIgnoreCase(type) && config != null) {
        List<String> capped = ChannelSuggestionValidator.dedupeAndCapWebUrls(config.resolveUrls());
        if (capped.isEmpty() && StrUtil.isNotBlank(baseUrl)) {
          capped = ChannelSuggestionValidator.dedupeAndCapWebUrls(List.of(baseUrl));
        }
        config.setUrls(capped);
        if (!capped.isEmpty()) {
          baseUrl = capped.get(0);
        }
      }
      if (StrUtil.isBlank(name) && StrUtil.isBlank(type) && StrUtil.isBlank(baseUrl)) {
        continue;
      }
      out.add(new AiChannelCandidate(name, type, baseUrl, config));
    }
    return out;
  }

  /**
   * 解析 config 对象为 {@link ChannelFetchConfig}。
   *
   * @param raw JSON 节点
   * @return 配置；空则默认
   */
  static ChannelFetchConfig parseConfig(Object raw) {
    ChannelFetchConfig config = ChannelFetchConfig.builder().build();
    JSONObject obj;
    if (raw instanceof JSONObject jsonObject) {
      obj = jsonObject;
    } else if (raw != null) {
      try {
        obj = JSONUtil.parseObj(raw);
      } catch (Exception ignored) {
        return config;
      }
    } else {
      return config;
    }
    config.setQuery(StrUtil.trim(StrUtil.nullToEmpty(obj.getStr("query"))));
    config.setTopic(StrUtil.trim(StrUtil.nullToEmpty(obj.getStr("topic"))));
    config.setSearchDepth(StrUtil.trim(StrUtil.nullToEmpty(obj.getStr("searchDepth"))));
    config.setDateField(StrUtil.trim(StrUtil.nullToEmpty(obj.getStr("dateField"))));
    config.setSort(StrUtil.trim(StrUtil.nullToEmpty(obj.getStr("sort"))));
    if (obj.get("maxResults") != null) {
      config.setMaxResults(obj.getInt("maxResults"));
    }
    List<String> queries = new ArrayList<>();
    JSONArray queryArr = obj.getJSONArray("queries");
    if (queryArr != null) {
      for (Object item : queryArr) {
        String text = StrUtil.trim(StrUtil.nullToEmpty(String.valueOf(item)));
        if (StrUtil.isNotBlank(text)) {
          queries.add(text);
        }
      }
    }
    config.setQueries(queries);
    List<String> urls = new ArrayList<>();
    JSONArray urlArr = obj.getJSONArray("urls");
    if (urlArr != null) {
      for (Object item : urlArr) {
        String text = StrUtil.trim(StrUtil.nullToEmpty(String.valueOf(item)));
        if (StrUtil.isNotBlank(text)) {
          urls.add(text);
        }
      }
    }
    config.setUrls(urls);
    return config;
  }

  static JSONObject extractJsonObject(String text) {
    if (StrUtil.isBlank(text)) {
      return null;
    }
    String raw = text.trim();
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start >= 0 && end > start) {
      raw = raw.substring(start, end + 1);
    }
    try {
      return JSONUtil.parseObj(raw);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 解析 JSON 对象为 map。
   *
   * @param json JSON 字符串
   * @return map；失败为空
   */
  static Map<String, Object> readJsonMap(String json) {
    if (StrUtil.isBlank(json)) {
      return Map.of();
    }
    try {
      Object parsed = JSONUtil.parse(json);
      if (parsed instanceof Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (entry.getKey() != null) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
          }
        }
        return out;
      }
    } catch (Exception ignored) {
      // 忽略坏 JSON
    }
    return Map.of();
  }

  /**
   * 取嵌套 map。
   *
   * @param root 根
   * @param key  键
   * @return 子 map；无则空
   */
  static Map<String, Object> nestedMap(Map<String, Object> root, String key) {
    if (root == null) {
      return Map.of();
    }
    Object raw = root.get(key);
    if (!(raw instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() != null) {
        out.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    }
    return out;
  }

  /**
   * AI 候选渠道（解析后强类型）。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static class AiChannelCandidate {
    /** 名称。 */
    private final String name;
    /** 类型字面量。 */
    private final String type;
    /** 入口 URL。 */
    private final String baseUrl;
    /** 扩展配置。 */
    private final ChannelFetchConfig config;
  }


}
