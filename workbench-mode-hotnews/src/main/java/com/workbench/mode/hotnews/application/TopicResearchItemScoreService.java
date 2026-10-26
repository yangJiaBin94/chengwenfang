package com.workbench.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.constant.ModelCallScenes;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.ai.facade.dto.view.TextCompleteResult;
import com.workbench.mode.hotnews.application.model.ResearchCandidate;
import com.workbench.mode.hotnews.application.prompt.ResearchItemScorePrompts;
import com.workbench.mode.hotnews.domain.ResearchItemScoreDetails;
import com.workbench.mode.hotnews.domain.TopicDimensionScore;
import com.workbench.mode.hotnews.domain.entity.TopicClusterEntity;
import com.workbench.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.workbench.mode.hotnews.facade.constant.ResearchItemScoringRubric;
import com.workbench.mode.hotnews.facade.enums.ResearchItemKind;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资料池素材五维 AI 打分：对齐雷达「分维 + 服务端加权」模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicResearchItemScoreService {
  private static final int BATCH_SIZE = 10;

  private final AiTextFacade ai;
  private final TopicClusterMapper topicMapper;
  private final TopicResearchItemMapper researchItemMapper;
  private final ResearchItemScoreDetailsStore scoreDetailsStore;

  /**
   * 对候选列表批量 AI 打分并写回 score / scoreDetails；失败条目回退 kind 默认分。
   *
   * @param topicTitle 选题标题
   * @param topicSummary 选题摘要
   * @param candidates 候选（就地更新）
   * @return 同一列表
   */
  public List<ResearchCandidate> scoreCandidates(
      String topicTitle, String topicSummary, List<ResearchCandidate> candidates) {
    if (CollUtil.isEmpty(candidates)) {
      return candidates == null ? List.of() : candidates;
    }
    // 先写入 kind 默认分，AI 成功后再覆盖
    for (ResearchCandidate candidate : candidates) {
      applyFallbackScore(candidate);
    }
    List<TopicDimensionScore> rubric = ResearchItemScoringRubric.dimensions();
    for (int start = 0; start < candidates.size(); start += BATCH_SIZE) {
      int end = Math.min(start + BATCH_SIZE, candidates.size());
      List<ResearchCandidate> batch = candidates.subList(start, end);
      scoreBatch(topicTitle, topicSummary, rubric, batch, start);
    }
    return candidates;
  }

  /**
   * 视频解析成功后，按更新后的摘要对已入库条目重打分。
   *
   * @param item 资料池条目（须已有 summary）
   */
  public void rescorePersistedItem(TopicResearchItemEntity item) {
    if (item == null || StrUtil.isBlank(item.getId()) || StrUtil.isBlank(item.getTopicId())) {
      return;
    }
    TopicClusterEntity topic = topicMapper.selectById(item.getTopicId());
    String topicTitle = topic == null ? "" : StrUtil.nullToEmpty(topic.getTitle());
    String topicSummary = topic == null ? "" : StrUtil.nullToEmpty(topic.getSummary());
    ResearchCandidate candidate = ResearchCandidate.builder()
        .title(StrUtil.nullToEmpty(item.getTitle()))
        .url(StrUtil.nullToEmpty(item.getUrl()))
        .summary(StrUtil.nullToEmpty(item.getSummary()))
        .kind(ResearchItemKind.from(item.getKind()))
        .score(item.getScore() == null ? 0 : item.getScore())
        .build();
    scoreCandidates(topicTitle, topicSummary, List.of(candidate));
    item.setScore(candidate.getScore());
    researchItemMapper.updateById(item);
    scoreDetailsStore.write(item.getId(), candidate.getScoreDetails());
    log.info(
        "资料池条目重打分完成 itemId={} score={}",
        item.getId(),
        candidate.getScore());
  }

  /**
   * kind 默认回退分：source 70 / web 80 / manual 85。
   *
   * @param candidate 候选
   */
  public static void applyFallbackScore(ResearchCandidate candidate) {
    if (candidate == null) {
      return;
    }
    ResearchItemKind kind = candidate.getKind() == null ? ResearchItemKind.WEB : candidate.getKind();
    int fallback = switch (kind) {
      case SOURCE -> 70;
      case MANUAL -> 85;
      case WEB -> 80;
    };
    candidate.setScore(fallback);
    if (candidate.getScoreDetails() == null) {
      candidate.setScoreDetails(ResearchItemScoreDetails.builder()
          .rubricVersion(ResearchItemScoringRubric.VERSION)
          .dimensions(List.of())
          .recommendUse("supplement")
          .riskNote("使用默认分（AI 未评分）")
          .build());
    }
  }

  /**
   * 将评分明细序列化为 JSON 字符串。
   *
   * @param details 明细
   * @return JSON；空则 null
   */
  public static String serializeDetails(ResearchItemScoreDetails details) {
    if (details == null) {
      return null;
    }
    return JSONUtil.toJsonStr(details);
  }

  /**
   * 从 JSON 反序列化评分明细。
   *
   * @param json JSON 文本
   * @return 明细；失败返回 null
   */
  public static ResearchItemScoreDetails parseDetails(String json) {
    if (StrUtil.isBlank(json)) {
      return null;
    }
    try {
      return JSONUtil.toBean(json, ResearchItemScoreDetails.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  private void scoreBatch(
      String topicTitle,
      String topicSummary,
      List<TopicDimensionScore> rubric,
      List<ResearchCandidate> batch,
      int idOffset) {
    List<Map<String, Object>> payloadItems = new ArrayList<>();
    for (int index = 0; index < batch.size(); index++) {
      ResearchCandidate candidate = batch.get(index);
      String itemSummary = StrUtil.nullToEmpty(candidate.getSummary());
      if (itemSummary.length() > 500) {
        itemSummary = itemSummary.substring(0, 500);
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", String.valueOf(idOffset + index));
      row.put("title", StrUtil.nullToEmpty(candidate.getTitle()));
      row.put("summary", itemSummary);
      row.put("url", StrUtil.nullToEmpty(candidate.getUrl()));
      row.put(
          "kind",
          candidate.getKind() == null
              ? ResearchItemKind.WEB.getValue()
              : candidate.getKind().getValue());
      payloadItems.add(row);
    }
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("topicTitle", StrUtil.nullToEmpty(topicTitle));
    user.put("topicSummary", StrUtil.nullToEmpty(topicSummary));
    user.put("rubricVersion", ResearchItemScoringRubric.VERSION);
    user.put("dimensions", rubricPayload(rubric));
    user.put("items", payloadItems);
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_RESEARCH_ITEM_SCORE)
          .systemPrompt(ResearchItemScorePrompts.SYSTEM_PROMPT)
          .prompt(JSONUtil.toJsonStr(user))
          .timeoutSeconds(120)
          .build());
      Map<String, ScoredItem> scored = parseScoredItems(result == null ? null : result.getText());
      if (scored.isEmpty()) {
        log.warn("资料池 AI 评分未解析到结果，保留默认分 batchSize={}", batch.size());
        return;
      }
      for (int index = 0; index < batch.size(); index++) {
        ScoredItem hit = scored.get(String.valueOf(idOffset + index));
        if (hit == null) {
          continue;
        }
        applyScored(batch.get(index), hit);
      }
      log.info("资料池 AI 评分完成 batchSize={} scored={}", batch.size(), scored.size());
    } catch (Exception exception) {
      log.warn("资料池 AI 评分失败，保留默认分 err={}", exception.getMessage());
    }
  }

  private static void applyScored(ResearchCandidate candidate, ScoredItem scored) {
    List<TopicDimensionScore> dims = ResearchItemScoringRubric.fillDimensions(
        scored.scoreByKey(), scored.reasonByKey());
    int overall = ResearchItemScoringRubric.computeOverall(scored.scoreByKey());
    candidate.setScore(overall);
    candidate.setScoreDetails(ResearchItemScoreDetails.builder()
        .rubricVersion(ResearchItemScoringRubric.VERSION)
        .dimensions(dims)
        .recommendUse(scored.recommendUse())
        .riskNote(scored.riskNote())
        .build());
  }

  private static List<Map<String, Object>> rubricPayload(List<TopicDimensionScore> rubric) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (TopicDimensionScore dim : rubric) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("key", dim.getKey());
      row.put("label", dim.getLabel());
      row.put("weight", dim.getWeight());
      row.put("description", dim.getReason());
      out.add(row);
    }
    return out;
  }

  private static Map<String, ScoredItem> parseScoredItems(String text) {
    Map<String, ScoredItem> out = new LinkedHashMap<>();
    JSONObject root = extractJsonObject(text);
    if (root == null) {
      return out;
    }
    JSONArray items = root.getJSONArray("items");
    if (items == null) {
      return out;
    }
    for (Object raw : items) {
      if (!(raw instanceof JSONObject row)) {
        continue;
      }
      String id = StrUtil.trim(StrUtil.nullToEmpty(row.getStr("id")));
      if (StrUtil.isBlank(id)) {
        continue;
      }
      Map<String, Integer> scoreByKey = new LinkedHashMap<>();
      Map<String, String> reasonByKey = new LinkedHashMap<>();
      JSONArray dims = row.getJSONArray("dimensions");
      if (dims != null) {
        for (Object dimRaw : dims) {
          if (!(dimRaw instanceof JSONObject dim)) {
            continue;
          }
          String key = ResearchItemScoringRubric.normalizeKey(dim.getStr("key"));
          if (StrUtil.isBlank(key)) {
            continue;
          }
          Integer score = dim.getInt("score");
          scoreByKey.put(key, score == null ? 0 : Math.max(0, Math.min(10, score)));
          reasonByKey.put(key, StrUtil.trim(StrUtil.nullToEmpty(dim.getStr("reason"))));
        }
      }
      out.put(
          id,
          new ScoredItem(
              scoreByKey,
              reasonByKey,
              normalizeRecommendUse(row.getStr("recommendUse")),
              StrUtil.trim(StrUtil.nullToEmpty(row.getStr("riskNote")))));
    }
    return out;
  }

  private static String normalizeRecommendUse(String raw) {
    String value = StrUtil.trim(StrUtil.nullToEmpty(raw)).toLowerCase(Locale.ROOT);
    return switch (value) {
      case "priority", "supplement", "cautious" -> value;
      default -> "supplement";
    };
  }

  private static JSONObject extractJsonObject(String text) {
    if (StrUtil.isBlank(text)) {
      return null;
    }
    String raw = text.trim();
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return null;
    }
    try {
      return JSONUtil.parseObj(raw.substring(start, end + 1));
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 单条素材 AI 打分结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  private static final class ScoredItem {
    /** 分维得分。 */
    private final Map<String, Integer> scoreByKey;
    /** 分维理由。 */
    private final Map<String, String> reasonByKey;
    /** 推荐用途。 */
    private final String recommendUse;
    /** 风险备注。 */
    private final String riskNote;
  }
}
