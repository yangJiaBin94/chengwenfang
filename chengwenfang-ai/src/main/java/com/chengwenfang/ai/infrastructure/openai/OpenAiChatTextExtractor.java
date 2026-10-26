package com.chengwenfang.ai.infrastructure.openai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat Completions 文本提取，对齐 Python {@code text_client._extract_text} +
 * LangChain {@code ChatOpenAI}（官方规格只认 {@code message.content}）。
 *
 * <p>明确忽略第三方字段 {@code reasoning_content} / {@code reasoning_details}，
 * 与 Python ChatOpenAI 文档一致。
 */
public final class OpenAiChatTextExtractor {
  private OpenAiChatTextExtractor() {}

  /**
   * 从 {@code /chat/completions} 响应 JSON 取出最终助手正文。
   *
   * @param body 上游响应原文
   * @return 去除首尾空白的正文；无法解析时返回空串
   */
  public static String extractFromResponseBody(String body) {
    if (StrUtil.isBlank(body)) {
      return "";
    }
    JSONObject root;
    try {
      root = JSONUtil.parseObj(body);
    } catch (Exception ex) {
      return "";
    }
    JSONArray choices = root.getJSONArray("choices");
    if (choices == null || choices.isEmpty()) {
      return "";
    }
    JSONObject first = choices.getJSONObject(0);
    if (first == null) {
      return "";
    }
    JSONObject message = first.getJSONObject("message");
    if (message == null) {
      return "";
    }
    // 故意不读取 reasoning_content / reasoning_details
    return extractContent(message.get("content"));
  }

  /**
   * 对齐 Python {@code _extract_text}：字符串直接返回；多段则优先 {@code type=text} 块
   *（等同 {@code AIMessage.text}），否则拼接各段 {@code text} 字段。
   */
  static String extractContent(Object content) {
    if (content == null) {
      return "";
    }
    if (content instanceof CharSequence sequence) {
      return sequence.toString().strip();
    }
    if (content instanceof JSONArray array) {
      return extractFromParts(array);
    }
    if (content instanceof List<?> list) {
      JSONArray array = new JSONArray();
      array.addAll(list);
      return extractFromParts(array);
    }
    return "";
  }

  private static String extractFromParts(JSONArray parts) {
    // 对齐 AIMessage.text：只取 type 缺失或为 text / output_text 的块
    List<String> typedText = new ArrayList<>();
    // 对齐 _extract_text 回退：任意带 text 字段的 dict / 纯字符串
    List<String> anyText = new ArrayList<>();
    for (Object raw : parts) {
      if (raw instanceof CharSequence sequence) {
        String value = sequence.toString();
        typedText.add(value);
        anyText.add(value);
        continue;
      }
      if (!(raw instanceof JSONObject part)) {
        continue;
      }
      String text = part.getStr("text");
      if (text == null) {
        continue;
      }
      anyText.add(text);
      String type = part.getStr("type");
      if (type == null || "text".equals(type) || "output_text".equals(type)) {
        typedText.add(text);
      }
    }
    String preferred = String.join("", typedText).strip();
    if (StrUtil.isNotBlank(preferred)) {
      return preferred;
    }
    return String.join("\n", anyText).strip();
  }
}
