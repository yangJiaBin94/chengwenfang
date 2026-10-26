package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.constant.ModelCallScenes;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.mode.hotnews.application.prompt.ResearchTranslatePrompts;
import com.workbench.ai.facade.dto.view.TextCompleteResult;
import com.workbench.mode.hotnews.application.model.ResearchCandidate;
import com.workbench.mode.hotnews.domain.ResearchContentClassifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选题资料非中文标题/摘要翻译。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchTranslator {
  private final AiTextFacade ai;

  /**
   * 将非中文标题/摘要译为简体中文；失败则保留原文。
   *
   * @param candidates 候选资料
   * @return 就地更新后的列表（同一实例）
   */
  public List<ResearchCandidate> translateNonChinese(List<ResearchCandidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return candidates == null ? List.of() : candidates;
    }
    List<Integer> indexes = new ArrayList<>();
    List<Map<String, Object>> payloadItems = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      ResearchCandidate c = candidates.get(i);
      if (!ResearchContentClassifier.needsChineseTranslation(c.getTitle())
          && !ResearchContentClassifier.needsChineseTranslation(c.getSummary())) {
        continue;
      }
      indexes.add(i);
      String itemSummary = c.getSummary() == null ? "" : c.getSummary();
      if (itemSummary.length() > 600) {
        itemSummary = itemSummary.substring(0, 600);
      }
      // LinkedHashMap：固定键序，避免 Map.of 迭代顺序不稳定影响提示词
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", String.valueOf(i));
      item.put("title", StrUtil.nullToEmpty(c.getTitle()));
      item.put("summary", itemSummary);
      payloadItems.add(item);
    }
    if (payloadItems.isEmpty()) {
      return candidates;
    }
    try {
      Map<String, Object> translatePayload = new LinkedHashMap<>();
      translatePayload.put("items", payloadItems);
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_TOPIC_RESEARCH_TRANSLATE)
          .systemPrompt(ResearchTranslatePrompts.TRANSLATE_SYSTEM)
          .prompt(JSONUtil.toJsonStr(translatePayload))
          .timeoutSeconds(90)
          .build());
      Map<String, TranslatedText> translated = parseTranslations(result == null ? null : result.getText());
      int applied = 0;
      for (Integer index : indexes) {
        TranslatedText hit = translated.get(String.valueOf(index));
        if (hit == null) {
          continue;
        }
        ResearchCandidate c = candidates.get(index);
        String originalTitle = StrUtil.nullToEmpty(c.getTitle()).trim();
        if (StrUtil.isNotBlank(hit.title())) {
          c.setTitle(hit.title().trim());
          String summary = StrUtil.nullToEmpty(c.getSummary()).trim();
          if (StrUtil.isNotBlank(hit.summary())) {
            summary = hit.summary().trim();
          }
          if (StrUtil.isNotBlank(originalTitle) && !originalTitle.equals(c.getTitle())
              && !summary.contains(originalTitle)) {
            String note = "原标题：" + originalTitle;
            summary = StrUtil.isBlank(summary) ? note : note + "\n" + summary;
          }
          c.setSummary(summary);
          applied++;
        }
      }
      log.info("选题资料非中文翻译完成 need={} applied={}", indexes.size(), applied);
    } catch (Exception ex) {
      log.warn("选题资料非中文翻译失败，保留原文 err={}", ex.getMessage());
    }
    return candidates;
  }

  /**
   * 解析翻译结果 JSON。
   *
   * @param raw AI 原文
   * @return id → 译后文本
   */
  static Map<String, TranslatedText> parseTranslations(String raw) {
    Map<String, TranslatedText> out = new LinkedHashMap<>();
    JSONObject json = extractJsonObject(raw);
    if (json == null) {
      return out;
    }
    JSONArray items = json.getJSONArray("items");
    if (items == null) {
      return out;
    }
    for (Object row : items) {
      if (!(row instanceof JSONObject obj)) {
        continue;
      }
      String id = StrUtil.trim(obj.getStr("id"));
      if (StrUtil.isBlank(id)) {
        continue;
      }
      out.put(id, new TranslatedText(
          StrUtil.trim(obj.getStr("title")),
          StrUtil.trim(obj.getStr("summary"))));
    }
    return out;
  }

  /**
   * 译后标题与摘要。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  static final class TranslatedText {
    /** 标题。 */
    private final String title;
    /** 摘要。 */
    private final String summary;
  }

  private static JSONObject extractJsonObject(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    try {
      String text = raw.trim();
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start >= 0 && end > start) {
        text = text.substring(start, end + 1);
      }
      return JSONUtil.parseObj(text);
    } catch (Exception ignored) {
      return null;
    }
  }
}
