package com.chengwenfang.ai.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 AI 上游技术错误（HTML 拦截页、HTTP 原文、SDK 异常）转成用户可读中文。
 */
public final class AiUpstreamErrorMessages {

  /** 从 OpenAI 风格 JSON 错误体中提取 message。 */
  private static final Pattern JSON_ERROR_MESSAGE =
      Pattern.compile("\"message\"\\s*:\\s*\"([^\"]{1,300})\"", Pattern.CASE_INSENSITIVE);

  private AiUpstreamErrorMessages() {}

  /**
   * 生成用户可读的上游失败说明。
   *
   * @param rawMessage 原始异常或响应正文；可空
   * @return 短中文说明，不含 HTML / 堆栈
   */
  public static String humanize(String rawMessage) {
    if (StrUtil.isBlank(rawMessage)) {
      return "上游调用失败，请检查配置后重试。";
    }
    String text = rawMessage.strip();
    String lower = text.toLowerCase(Locale.ROOT);

    // 上游返回网页（常见于填成官网地址、被 WAF/CDN 拦截）
    if (looksLikeHtml(text)) {
      if (containsAny(lower, "request blocked", "access denied", "attention required", "cf-error",
          "just a moment", "cloudflare")) {
        return "请求被上游拦截，请确认「请求地址」是 API 地址（不是官网），并检查网络或代理。";
      }
      return "上游返回了网页而不是 API 结果，请检查「请求地址」是否填写正确。";
    }

    // 优先取上游 JSON 错误里的 message（比笼统状态码更具体）
    String jsonMessage = extractJsonErrorMessage(text);
    if (StrUtil.isNotBlank(jsonMessage)) {
      return truncate(jsonMessage, 200);
    }

    if (containsAny(lower, "timeout", "timed out", "read timed out", "sockettimeout")) {
      return "上游请求超时，请稍后重试或检查网络。";
    }
    if (containsAny(lower, "connection refused", "connect timed out", "unknownhost",
        "failed to connect", "network is unreachable", "no route to host")) {
      return "无法连接上游服务，请检查「请求地址」与网络。";
    }
    if (containsAny(lower, "unauthorized", "invalid api key", "incorrect api key",
        "invalid_api_key")
        || containsHttpStatus(lower, 401)) {
      return "API Key 无效或未授权，请检查密钥。";
    }
    if (containsAny(lower, "forbidden") || containsHttpStatus(lower, 403)) {
      return "上游拒绝访问（无权限），请检查密钥与模型权限。";
    }
    if (containsAny(lower, "model_not_found") || containsHttpStatus(lower, 404)) {
      return "上游接口或模型不存在，请检查请求地址与模型名称。";
    }
    if (containsAny(lower, "rate limit", "too many requests") || containsHttpStatus(lower, 429)) {
      return "上游请求过于频繁或额度不足，请稍后重试。";
    }
    if (containsAny(lower, "bad gateway", "service unavailable")
        || containsHttpStatus(lower, 500)
        || containsHttpStatus(lower, 502)
        || containsHttpStatus(lower, 503)
        || containsHttpStatus(lower, 504)) {
      return "上游服务暂时不可用，请稍后重试。";
    }

    // 已是较短中文业务说明则保留
    if (text.length() <= 160 && looksMostlyChinese(text)) {
      return text;
    }
    if (text.length() > 160) {
      return "上游调用失败：" + text.substring(0, 120).strip() + "…";
    }
    return "上游调用失败：" + text;
  }

  /**
   * 判断正文是否像 HTML 页面。
   *
   * @param text 原文
   * @return true 表示像 HTML
   */
  private static boolean looksLikeHtml(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    return lower.contains("<!doctype html")
        || lower.contains("<html")
        || (lower.contains("<head") && lower.contains("<body"))
        || lower.contains("</html>");
  }

  /**
   * 尝试从 JSON 错误体提取 message 字段。
   *
   * @param text 原文
   * @return 提取到的说明；失败返回 null
   */
  private static String extractJsonErrorMessage(String text) {
    String trimmed = text.strip();
    if (trimmed.startsWith("{") && trimmed.contains("\"error\"")) {
      try {
        JSONObject root = JSONUtil.parseObj(trimmed);
        JSONObject error = root.getJSONObject("error");
        if (error != null && StrUtil.isNotBlank(error.getStr("message"))) {
          return error.getStr("message").strip();
        }
      } catch (Exception ignored) {
        // 再走正则兜底
      }
    }
    Matcher matcher = JSON_ERROR_MESSAGE.matcher(text);
    if (matcher.find()) {
      return matcher.group(1).strip();
    }
    return null;
  }

  /**
   * 截断过长文案。
   *
   * @param text      原文
   * @param maxLength 最大长度
   * @return 截断后文案
   */
  private static String truncate(String text, int maxLength) {
    if (text.length() <= maxLength) {
      return text;
    }
    return text.substring(0, maxLength).strip() + "…";
  }

  /**
   * 是否包含任一子串。
   *
   * @param haystack 已小写或原文
   * @param needles  子串
   * @return 命中任一则 true
   */
  private static boolean containsAny(String haystack, String... needles) {
    for (String needle : needles) {
      if (haystack.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 判断是否像 HTTP 状态码出现（避免误伤正文里的普通数字）。
   *
   * @param lower  已小写原文
   * @param status 状态码
   * @return 命中则 true
   */
  private static boolean containsHttpStatus(String lower, int status) {
    String code = String.valueOf(status);
    return lower.contains("http " + code)
        || lower.contains("status=" + code)
        || lower.contains("status: " + code)
        || lower.contains("status code: " + code)
        || lower.contains("status_code=" + code)
        || lower.contains("\"code\":" + code)
        || lower.contains("\"code\": " + code)
        || lower.contains("error " + code)
        || lower.startsWith(code + " ")
        || lower.contains(" " + code + " ");
  }

  /**
   * 粗判是否以中文为主（便于保留已有业务文案）。
   *
   * @param text 原文
   * @return 中文不少于字母时为 true
   */
  private static boolean looksMostlyChinese(String text) {
    int chinese = 0;
    int letters = 0;
    for (int index = 0; index < text.length(); index++) {
      char ch = text.charAt(index);
      if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
        chinese++;
      } else if (Character.isLetter(ch)) {
        letters++;
      }
    }
    return chinese > 0 && chinese >= letters;
  }
}
