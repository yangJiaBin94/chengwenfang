package com.chengwenfang.ai.infrastructure.openai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.dto.view.WebSearchCitation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 DeepSeek Responses/Chat 联网响应：正文 + citations（URL 去重保序）。
 */
public final class DeepSeekWebSearchResponseParser {
  private DeepSeekWebSearchResponseParser() {}

  /**
   * 解析结果。
   *
   * @param text      模型正文
   * @param citations 引用列表
   */
  public record Parsed(String text, List<WebSearchCitation> citations) {}

  /**
   * 从 Responses 风格 JSON 提取正文与引用。
   *
   * @param body 响应体
   * @return 解析结果
   */
  public static Parsed parseResponses(String body) {
    if (StrUtil.isBlank(body)) {
      return new Parsed("", List.of());
    }
    JSONObject root = JSONUtil.parseObj(body);
    String text = StrUtil.blankToDefault(root.getStr("output_text"), "");
    Map<String, WebSearchCitation> byUrl = new LinkedHashMap<>();
    collectCitations(root, byUrl);
    if (StrUtil.isBlank(text)) {
      text = OpenAiResponsesTextExtractor.extract(
          JSONUtil.toBean(body, OpenAiResponsesResponse.class));
    }
    // 再扫 output 消息块上的 annotations/citations
    Object output = root.get("output");
    if (output instanceof JSONArray array) {
      StringBuilder fromMessages = new StringBuilder();
      for (Object itemObj : array) {
        if (!(itemObj instanceof JSONObject item)) {
          continue;
        }
        String type = StrUtil.nullToEmpty(item.getStr("type"));
        if (!"message".equals(type) && StrUtil.isNotBlank(type)) {
          continue;
        }
        Object content = item.get("content");
        if (!(content instanceof JSONArray parts)) {
          continue;
        }
        for (Object partObj : parts) {
          if (!(partObj instanceof JSONObject part)) {
            continue;
          }
          if ("output_text".equals(part.getStr("type")) || "text".equals(part.getStr("type"))) {
            String partText = part.getStr("text");
            if (StrUtil.isNotBlank(partText)) {
              fromMessages.append(partText);
            }
          }
          collectCitations(part, byUrl);
        }
      }
      if (StrUtil.isBlank(text) && fromMessages.length() > 0) {
        text = fromMessages.toString().strip();
      }
    }
    return new Parsed(StrUtil.nullToEmpty(text).strip(), List.copyOf(byUrl.values()));
  }

  /**
   * 从 Chat Completions 风格 JSON 提取正文与引用。
   *
   * @param body 响应体
   * @return 解析结果
   */
  public static Parsed parseChat(String body) {
    if (StrUtil.isBlank(body)) {
      return new Parsed("", List.of());
    }
    JSONObject root = JSONUtil.parseObj(body);
    Map<String, WebSearchCitation> byUrl = new LinkedHashMap<>();
    collectCitations(root, byUrl);
    String text = "";
    JSONArray choices = root.getJSONArray("choices");
    if (choices != null && !choices.isEmpty()) {
      JSONObject first = choices.getJSONObject(0);
      if (first != null) {
        JSONObject message = first.getJSONObject("message");
        if (message != null) {
          Object content = message.get("content");
          if (content instanceof String stringContent) {
            text = stringContent;
          } else if (content instanceof JSONArray parts) {
            StringBuilder builder = new StringBuilder();
            for (Object partObj : parts) {
              if (partObj instanceof JSONObject part) {
                if (StrUtil.isNotBlank(part.getStr("text"))) {
                  builder.append(part.getStr("text"));
                }
                collectCitations(part, byUrl);
              }
            }
            text = builder.toString();
          }
          collectCitations(message, byUrl);
        }
      }
    }
    return new Parsed(StrUtil.nullToEmpty(text).strip(), List.copyOf(byUrl.values()));
  }

  private static void collectCitations(Object node, Map<String, WebSearchCitation> byUrl) {
    if (node == null) {
      return;
    }
    if (node instanceof JSONObject object) {
      putCitation(object, byUrl);
      Object citations = object.get("citations");
      if (citations instanceof JSONArray array) {
        for (Object item : array) {
          if (item instanceof JSONObject citation) {
            putCitation(citation, byUrl);
          }
        }
      }
      Object annotations = object.get("annotations");
      if (annotations instanceof JSONArray array) {
        for (Object item : array) {
          if (item instanceof JSONObject annotation) {
            putCitation(annotation, byUrl);
            JSONObject nested = annotation.getJSONObject("url_citation");
            if (nested != null) {
              putCitation(nested, byUrl);
            }
          }
        }
      }
      // 工具结果里的网页条目
      Object results = object.get("results");
      if (results instanceof JSONArray array) {
        for (Object item : array) {
          if (item instanceof JSONObject result) {
            putCitation(result, byUrl);
          }
        }
      }
      for (String key : object.keySet()) {
        Object child = object.get(key);
        if (child instanceof JSONObject || child instanceof JSONArray) {
          if ("citations".equals(key) || "annotations".equals(key) || "results".equals(key)) {
            continue;
          }
          collectCitations(child, byUrl);
        }
      }
      return;
    }
    if (node instanceof JSONArray array) {
      for (Object item : array) {
        collectCitations(item, byUrl);
      }
    }
  }

  private static void putCitation(JSONObject object, Map<String, WebSearchCitation> byUrl) {
    if (object == null) {
      return;
    }
    String url = StrUtil.firstNonBlank(
        object.getStr("url"),
        object.getStr("link"),
        object.getStr("href"));
    if (StrUtil.isBlank(url)) {
      return;
    }
    String trimmed = url.trim();
    String lower = trimmed.toLowerCase();
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
      return;
    }
    if (byUrl.containsKey(trimmed)) {
      return;
    }
    String title = StrUtil.firstNonBlank(object.getStr("title"), object.getStr("site_name"), "");
    byUrl.put(trimmed, WebSearchCitation.builder().url(trimmed).title(StrUtil.nullToEmpty(title)).build());
  }
}
