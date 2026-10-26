package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.constant.ModelCallScenes;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.mode.hotnews.application.prompt.ResearchKeywordPrompts;
import com.workbench.ai.facade.dto.view.TextCompleteResult;
import com.workbench.mode.hotnews.application.research.ResearchKeywordPlan;
import com.workbench.mode.hotnews.domain.TopicResearchPlanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选题检索关键词 AI 提取与查询展开。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchKeywordExtractor {
  private final AiTextFacade ai;

  /**
   * AI 提取检索主体与关键词；失败时回退规则提取。
   *
   * @param title   选题标题
   * @param summary 选题摘要，可空
   * @return 检索计划
   */
  public ResearchKeywordPlan extractKeywords(String title, String summary) {
    String safeTitle = StrUtil.nullToEmpty(title).trim();
    String safeSummary = StrUtil.nullToEmpty(summary).trim();
    if (StrUtil.isBlank(safeTitle) && StrUtil.isBlank(safeSummary)) {
      return ResearchKeywordPlan.builder()
          .subject("AI news")
          .keywords(List.of("AI news"))
          .disambiguation("")
          .fromAi(false)
          .build();
    }
    try {
      // LinkedHashMap：固定键序，避免 Map.of 迭代顺序不稳定影响提示词
      Map<String, Object> keywordPayload = new LinkedHashMap<>();
      keywordPayload.put("title", safeTitle);
      keywordPayload.put(
          "summary", safeSummary.length() > 800 ? safeSummary.substring(0, 800) : safeSummary);
      String userPrompt = JSONUtil.toJsonStr(keywordPayload);
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_TOPIC_RESEARCH_KEYWORDS)
          .systemPrompt(ResearchKeywordPrompts.KEYWORD_SYSTEM)
          .prompt(userPrompt)
          .timeoutSeconds(60)
          .build());
      ResearchKeywordPlan parsed = parseKeywordPlan(result == null ? null : result.getText());
      if (parsed != null && StrUtil.isNotBlank(parsed.getSubject())
          && parsed.getKeywords() != null && !parsed.getKeywords().isEmpty()) {
        parsed.setFromAi(true);
        log.info("选题检索词 AI 提取成功 subject={} keywords={}",
            parsed.getSubject(), parsed.getKeywords().size());
        return parsed;
      }
    } catch (Exception ex) {
      log.warn("选题检索词 AI 提取失败，回退规则 err={}", ex.getMessage());
    }
    return heuristicPlan(safeTitle, safeSummary);
  }

  /**
   * 由检索计划展开为实际搜索查询（主体词 + 官网/声明等角度）。
   *
   * @param plan 关键词计划
   * @return 去重后的查询列表
   */
  public List<String> buildSearchQueries(ResearchKeywordPlan plan) {
    if (plan == null) {
      return List.of("AI news");
    }
    return TopicResearchPlanner.buildQueriesFromPlan(
        plan.getSubject(), plan.getKeywords(), plan.getDisambiguation());
  }

  private static ResearchKeywordPlan heuristicPlan(String title, String summary) {
    String subject = TopicResearchPlanner.extractSubject(title);
    String shortQuery = TopicResearchPlanner.extractShortQuery(subject);
    List<String> keywords = new ArrayList<>();
    if (StrUtil.isNotBlank(shortQuery)) {
      keywords.add(shortQuery);
    }
    if (StrUtil.isNotBlank(subject) && !subject.equals(shortQuery)) {
      keywords.add(subject);
    }
    return ResearchKeywordPlan.builder()
        .subject(StrUtil.blankToDefault(subject, "AI news"))
        .keywords(keywords.isEmpty() ? List.of("AI news") : keywords)
        .disambiguation("")
        .fromAi(false)
        .build();
  }

  /**
   * 解析关键词计划 JSON。
   *
   * @param raw AI 原文
   * @return 计划；解析失败 null
   */
  static ResearchKeywordPlan parseKeywordPlan(String raw) {
    JSONObject json = extractJsonObject(raw);
    if (json == null) {
      return null;
    }
    String subject = StrUtil.trim(json.getStr("subject"));
    List<String> keywords = new ArrayList<>();
    JSONArray arr = json.getJSONArray("keywords");
    if (arr != null) {
      for (Object item : arr) {
        String kw = StrUtil.trim(String.valueOf(item));
        if (StrUtil.isNotBlank(kw) && !keywords.contains(kw)) {
          keywords.add(kw);
        }
      }
    }
    if (StrUtil.isBlank(subject) && keywords.isEmpty()) {
      return null;
    }
    if (StrUtil.isBlank(subject) && !keywords.isEmpty()) {
      subject = keywords.get(0);
    }
    return ResearchKeywordPlan.builder()
        .subject(subject)
        .keywords(keywords)
        .disambiguation(StrUtil.trim(json.getStr("disambiguation")))
        .fromAi(true)
        .build();
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
