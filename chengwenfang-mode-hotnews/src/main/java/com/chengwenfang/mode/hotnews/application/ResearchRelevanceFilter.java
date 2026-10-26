package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.mode.hotnews.application.prompt.ResearchRelevancePrompts;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.application.research.ResearchKeywordPlan;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选题资料相关性核验与成文参考条目筛选。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchRelevanceFilter {
  private static final int MAX_REFERENCE_ITEMS = 12;

  private final AiTextFacade ai;

  /**
   * 选取可用于骨架/成文的参考资料：未解析成功的视频跳过。
   *
   * @param items 资料池条目
   * @return 过滤后的参考列表
   */
  public static List<HotNewsResearchItemView> filterArticleReferences(
      List<HotNewsResearchItemView> items) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    List<HotNewsResearchItemView> out = new ArrayList<>();
    for (HotNewsResearchItemView item : items) {
      if (item == null) {
        continue;
      }
      boolean video = ResearchContentClassifier.isVideo(item.getUrl(), item.getTitle())
          || "视频".equals(item.getMediaLabel());
      if (video) {
        // 仅纳入解析成功且已有 AI 摘要的视频
        boolean parsedOk = VideoParseStatus.from(item.getVideoParseStatus()).isSucceeded();
        if (!parsedOk || StrUtil.isBlank(item.getSummary())) {
          continue;
        }
      }
      out.add(item);
      if (out.size() >= MAX_REFERENCE_ITEMS) {
        break;
      }
    }
    return out;
  }

  /**
   * AI 核验候选资料是否与选题同一话题；不相关则剔除。
   * <p>AI 失败时保守保留全部候选，避免资料池被误清空。
   *
   * @param title      选题标题
   * @param summary    选题摘要
   * @param plan       检索计划（含消歧）
   * @param candidates 候选资料
   * @return 相关资料子集（保持原顺序）
   */
  public List<ResearchCandidate> filterRelevant(
      String title,
      String summary,
      ResearchKeywordPlan plan,
      List<ResearchCandidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> payloadItems = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      ResearchCandidate c = candidates.get(i);
      String itemSummary = c.getSummary() == null ? "" : c.getSummary();
      if (itemSummary.length() > 400) {
        itemSummary = itemSummary.substring(0, 400);
      }
      // LinkedHashMap：固定键序，避免 Map.of 迭代顺序不稳定影响提示词
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", String.valueOf(i));
      item.put("title", StrUtil.nullToEmpty(c.getTitle()));
      item.put("summary", itemSummary);
      item.put("url", StrUtil.nullToEmpty(c.getUrl()));
      payloadItems.add(item);
    }
    try {
      Map<String, Object> user = new LinkedHashMap<>();
      user.put("topicTitle", StrUtil.nullToEmpty(title));
      user.put("topicSummary", StrUtil.nullToEmpty(summary));
      user.put("subject", plan == null ? "" : StrUtil.nullToEmpty(plan.getSubject()));
      user.put("disambiguation", plan == null ? "" : StrUtil.nullToEmpty(plan.getDisambiguation()));
      user.put("items", payloadItems);
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_TOPIC_RESEARCH_RELEVANCE)
          .systemPrompt(ResearchRelevancePrompts.RELEVANCE_SYSTEM)
          .prompt(JSONUtil.toJsonStr(user))
          .timeoutSeconds(90)
          .build());
      Map<String, Boolean> judgments = parseRelevance(result == null ? null : result.getText());
      if (judgments.isEmpty()) {
        log.warn("选题资料相关性核验未解析到结果，保留全部候选 count={}", candidates.size());
        return candidates;
      }
      List<ResearchCandidate> kept = new ArrayList<>();
      for (int i = 0; i < candidates.size(); i++) {
        Boolean ok = judgments.get(String.valueOf(i));
        if (ok == null || ok) {
          kept.add(candidates.get(i));
        } else {
          log.info("过滤不相关资料 title={}",
              StrUtil.sub(StrUtil.nullToEmpty(candidates.get(i).getTitle()), 0, 80));
        }
      }
      log.info("选题资料相关性核验完成 in={} kept={}", candidates.size(), kept.size());
      return kept;
    } catch (Exception ex) {
      log.warn("选题资料相关性核验失败，保留全部候选 err={}", ex.getMessage());
      return candidates;
    }
  }

  /**
   * 解析相关性判断 JSON。
   *
   * @param raw AI 原文
   * @return id → relevant
   */
  static Map<String, Boolean> parseRelevance(String raw) {
    Map<String, Boolean> out = new LinkedHashMap<>();
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
      Boolean relevant = obj.getBool("relevant");
      if (relevant != null) {
        out.put(id, relevant);
      }
    }
    return out;
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
