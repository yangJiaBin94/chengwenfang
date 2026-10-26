package com.chengwenfang.app.golden.record;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 从 AI WireMock 请求日志提取 AI 请求序列。
 * <p>请求体若为 JSON，按键排序后导出，消除 JVM Map 键序抖动（不改业务提示词语义）。
 */
public final class AiRecorder {
  private WireMockServer server;

  /**
   * 绑定 WireMock。
   *
   * @param server AI 服务；可空
   */
  public void bind(WireMockServer server) {
    this.server = server;
  }

  /**
   * 导出为 jsonl 文本（每行一条请求：method、url、body）。
   *
   * @return jsonl；未绑定或无请求时为空串
   */
  public String exportJsonl() {
    if (server == null) {
      return "";
    }
    List<LoggedRequest> requests =
        server.findAll(
            com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor(
                com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
    StringBuilder builder = new StringBuilder();
    for (LoggedRequest request : requests) {
      String url = request.getUrl();
      if (url == null) {
        continue;
      }
      boolean aiPath =
          url.contains("/chat/completions")
              || url.contains("/responses")
              || url.contains("/images/generations");
      if (!aiPath) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("method", request.getMethod().getName());
      row.put("url", url);
      row.put("body", canonicalizeJsonBody(request.getBodyAsString()));
      builder.append(JSONUtil.toJsonStr(row)).append('\n');
    }
    return builder.toString();
  }

  private static String canonicalizeJsonBody(String body) {
    String text = StrUtil.nullToEmpty(body);
    if (StrUtil.isBlank(text) || !JSONUtil.isTypeJSON(text)) {
      return text;
    }
    return JSONUtil.toJsonStr(sortKeys(JSONUtil.parse(text)));
  }

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
