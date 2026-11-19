package com.chengwenfang.ai.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import com.chengwenfang.ai.facade.constant.AiWebSearchConstants;
import com.chengwenfang.ai.facade.enums.AiProtocol;
import com.chengwenfang.ai.facade.enums.WebSearchMode;
import com.chengwenfang.ai.facade.dto.view.WebSearchCitation;
import com.chengwenfang.ai.infrastructure.openai.DeepSeekWebSearchResponseParser;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * DeepSeek 服务端联网工具调用（Responses / Chat Completions 裸 HTTP）。
 */
@Slf4j
public final class DeepSeekWebSearchClient {
  private DeepSeekWebSearchClient() {}

  /**
   * 联网补全结果。
   *
   * @param text      正文
   * @param model     实际模型
   * @param protocol  协议
   * @param citations 引用
   */
  public record Result(
      String text, String model, String protocol, List<WebSearchCitation> citations) {}

  /**
   * 调用 DeepSeek 联网工具。
   *
   * @param cfg            配置（须为 DeepSeek 端点）
   * @param apiKey         明文 Key
   * @param systemPrompt   系统提示；可空
   * @param userPrompt     用户提示
   * @param mode           ON 或 DEEP
   * @param timeoutSeconds 超时秒
   * @return 正文与 citations
   */
  public static Result complete(
      AiProviderConfigEntity cfg,
      String apiKey,
      String systemPrompt,
      String userPrompt,
      WebSearchMode mode,
      int timeoutSeconds) {
    if (cfg == null || StrUtil.isBlank(apiKey)) {
      throw new BusinessException(ErrorCodes.AI_KEY_MISSING, "未配置多模态模型 API Key", 400);
    }
    if (mode == null || !mode.isEnabled()) {
      throw new BusinessException(ErrorCodes.TEXT_FAILED, "未开启联网搜索模式", 400);
    }
    String model = AiWebSearchCapability.resolveWebSearchModel(cfg);
    AiProtocol protocol = AiProtocol.from(cfg.getProtocol());
    // DeepSeek 联网端点在根路径 /responses、/chat/completions；配置里常见 .../v1 需剥掉
    String baseUrl = resolveDeepSeekRootBaseUrl(cfg.getBaseUrl());
    String bodyJson;
    String url;
    if (protocol == AiProtocol.OPENAI_RESPONSES) {
      url = baseUrl + "/responses";
      bodyJson = buildResponsesBody(model, systemPrompt, userPrompt, mode, cfg);
    } else {
      url = baseUrl + "/chat/completions";
      bodyJson = buildChatBody(model, systemPrompt, userPrompt, mode, cfg);
    }
    log.info(
        "DeepSeek 联网补全开始 protocol={} model={} mode={} timeoutSec={}",
        protocol.getValue(),
        model,
        mode.getValue(),
        timeoutSeconds);
    try (HttpResponse response = HttpRequest.post(url)
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .timeout(Math.max(1, timeoutSeconds) * 1000)
        .body(bodyJson)
        .execute()) {
      if (!response.isOk()) {
        throw new BusinessException(
            ErrorCodes.TEXT_FAILED,
            "DeepSeek 联网补全失败：" + AiUpstreamErrorMessages.humanize(response.body()),
            502);
      }
      DeepSeekWebSearchResponseParser.Parsed parsed =
          protocol == AiProtocol.OPENAI_RESPONSES
              ? DeepSeekWebSearchResponseParser.parseResponses(response.body())
              : DeepSeekWebSearchResponseParser.parseChat(response.body());
      if (StrUtil.isBlank(parsed.text())) {
        throw new BusinessException(ErrorCodes.TEXT_FAILED, "DeepSeek 联网补全未返回可用内容", 502);
      }
      log.info(
          "DeepSeek 联网补全完成 citations={} textChars={}",
          parsed.citations().size(),
          parsed.text().length());
      return new Result(parsed.text(), model, protocol.getValue(), parsed.citations());
    }
  }

  private static String buildResponsesBody(
      String model,
      String systemPrompt,
      String userPrompt,
      WebSearchMode mode,
      AiProviderConfigEntity cfg) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("stream", false);
    body.put("tools", webTools());
    if (mode == WebSearchMode.DEEP) {
      body.put("reasoning", Map.of("effort", "high"));
    }
    if (cfg.getMaxOutputTokens() != null && cfg.getMaxOutputTokens() > 0) {
      body.put("max_output_tokens", cfg.getMaxOutputTokens());
    }
    if (cfg.getTemperature() != null) {
      body.put("temperature", cfg.getTemperature());
    }
    if (StrUtil.isNotBlank(systemPrompt)) {
      body.put("instructions", systemPrompt);
    }
    body.put("input", StrUtil.nullToEmpty(userPrompt));
    return JSONUtil.toJsonStr(body);
  }

  private static String buildChatBody(
      String model,
      String systemPrompt,
      String userPrompt,
      WebSearchMode mode,
      AiProviderConfigEntity cfg) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("stream", false);
    body.put("tools", webTools());
    if (mode == WebSearchMode.DEEP) {
      body.put("thinking", Map.of("type", "enabled"));
    } else {
      body.put("thinking", Map.of("type", "disabled"));
    }
    if (cfg.getMaxOutputTokens() != null && cfg.getMaxOutputTokens() > 0) {
      body.put("max_tokens", cfg.getMaxOutputTokens());
    }
    if (cfg.getTemperature() != null) {
      body.put("temperature", cfg.getTemperature());
    }
    JSONArray messages = new JSONArray();
    if (StrUtil.isNotBlank(systemPrompt)) {
      messages.add(new JSONObject(Map.of("role", "system", "content", systemPrompt)));
    }
    messages.add(new JSONObject(Map.of("role", "user", "content", StrUtil.nullToEmpty(userPrompt))));
    body.put("messages", messages);
    return JSONUtil.toJsonStr(body);
  }

  private static List<Map<String, String>> webTools() {
    // Responses / Chat Completions：仅声明 type，是否搜由模型判断
    return List.of(
        Map.of("type", AiWebSearchConstants.DEEPSEEK_WEB_SEARCH_TOOL),
        Map.of("type", AiWebSearchConstants.DEEPSEEK_READ_URL_TOOL));
  }

  /**
   * DeepSeek API 根地址：去掉末尾斜杠与可选的 {@code /v1}。
   *
   * @param raw 配置 baseUrl
   * @return 可拼 {@code /responses} 的根 URL
   */
  static String resolveDeepSeekRootBaseUrl(String raw) {
    String url = StrUtil.trim(StrUtil.nullToEmpty(raw));
    while (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    if (url.toLowerCase().endsWith("/v1")) {
      url = url.substring(0, url.length() - 3);
      while (url.endsWith("/")) {
        url = url.substring(0, url.length() - 1);
      }
    }
    return url;
  }
}
