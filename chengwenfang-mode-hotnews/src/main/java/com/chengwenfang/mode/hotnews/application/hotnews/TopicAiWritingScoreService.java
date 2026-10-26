package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.mode.hotnews.application.prompt.TopicWritingScorePrompts;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.HotNewsThemeBriefingSupport;
import com.chengwenfang.content.facade.ScoringRubricSupport;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricDimensionView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricView;
import com.chengwenfang.mode.hotnews.domain.TopicDimensionScore;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.request.TopicWritingScoreApplyRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 按主题评分量表对雷达选题做 AI 写作价值打分，并写回热点域。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicAiWritingScoreService {
  private final ContentFacade content;
  private final HotNewsFacade hotNews;
  private final AiTextFacade ai;

  /**
   * 对本雷达批次选题按主题量表 AI 评分。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @return 成功写回条数
   */
  public int scoreRadarBatch(String themeId, String radarBatchId) {
    return scoreRadarBatch(themeId, radarBatchId, null);
  }

  /**
   * 对本雷达批次选题按主题量表 AI 评分，并按条回调进度。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @param onProgress   进度回调 (已完成条数, 总条数)；可空
   * @return 成功写回条数
   */
  public int scoreRadarBatch(
      String themeId, String radarBatchId, BiConsumer<Integer, Integer> onProgress) {
    ScoringRubricView rubric = loadRubric(themeId);
    if (rubric == null || !ScoringRubricSupport.isValid(rubric)) {
      log.warn("主题缺少合法评分量表，跳过 AI 写作评分 themeId={}", themeId);
      return 0;
    }
    List<HotNewsTopicView> topics = hotNews.listRadarBatchTopics(themeId, radarBatchId);
    if (CollUtil.isEmpty(topics)) {
      return 0;
    }
    String themeContext = resolveThemeContext(themeId);
    List<TopicWritingScoreApplyRequest> applies = new ArrayList<>();
    int total = topics.size();
    int done = 0;
    for (HotNewsTopicView topic : topics) {
      if (topic == null || StrUtil.isBlank(topic.getId())) {
        done++;
        notifyProgress(onProgress, done, total);
        continue;
      }
      TopicWritingScoreApplyRequest scored = scoreOne(rubric, themeContext, topic);
      if (scored != null) {
        applies.add(scored);
      }
      done++;
      notifyProgress(onProgress, done, total);
    }
    int written = hotNews.applyWritingScores(applies);
    log.info(
        "雷达 AI 写作评分完成 themeId={} batch={} topics={} written={}",
        themeId,
        radarBatchId,
        topics.size(),
        written);
    return written;
  }

  /**
   * 安全触发进度回调。
   *
   * @param onProgress 回调；可空
   * @param done       已完成
   * @param total      总数
   */
  private static void notifyProgress(
      BiConsumer<Integer, Integer> onProgress, int done, int total) {
    if (onProgress == null) {
      return;
    }
    try {
      onProgress.accept(done, total);
    } catch (Exception ignored) {
      // 进度回调失败不阻断评分
    }
  }

  /**
   * 对单条选题打分。
   *
   * @param rubric       量表
   * @param themeContext 主题上下文
   * @param topic        选题
   * @return 写回请求；失败时返回 0 分占位
   */
  private TopicWritingScoreApplyRequest scoreOne(
      ScoringRubricView rubric, String themeContext, HotNewsTopicView topic) {
    List<TopicDimensionScore> snapshot = snapshotDims(rubric);
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(TopicWritingScorePrompts.SYSTEM_PROMPT)
          .prompt(buildUserPrompt(rubric, themeContext, topic))
          .scene(ModelCallScenes.HOT_NEWS_TOPIC_WRITING_SCORE)
          .timeoutSeconds(90)
          .build());
      return parseAndBuild(rubric, snapshot, topic.getId(), result == null ? null : result.getText());
    } catch (Exception exception) {
      log.warn(
          "选题 AI 写作评分失败 topicId={} err={}",
          topic.getId(),
          exception.getMessage());
      return failedApply(topic.getId(), snapshot, rubric.getVersion(), "AI 评分失败：" + exception.getMessage());
    }
  }

  private TopicWritingScoreApplyRequest parseAndBuild(
      ScoringRubricView rubric,
      List<TopicDimensionScore> snapshot,
      String topicId,
      String text) {
    JSONObject json = extractJsonObject(text);
    if (json == null) {
      return failedApply(topicId, snapshot, rubric.getVersion(), "AI 评分结果无法解析");
    }
    Map<String, Integer> scoreByKey = new LinkedHashMap<>();
    Map<String, String> reasonByKey = new LinkedHashMap<>();
    JSONArray dims = json.getJSONArray("dimensions");
    if (dims != null) {
      for (Object item : dims) {
        if (!(item instanceof JSONObject row)) {
          continue;
        }
        String key = StrUtil.trim(StrUtil.nullToEmpty(row.getStr("key"))).toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(key)) {
          continue;
        }
        Integer score = row.getInt("score");
        scoreByKey.put(key, score == null ? 0 : Math.max(0, Math.min(10, score)));
        reasonByKey.put(key, StrUtil.trim(StrUtil.nullToEmpty(row.getStr("reason"))));
      }
    }
    List<TopicDimensionScore> dimensionScores = new ArrayList<>();
    for (ScoringRubricDimensionView dim : rubric.getDimensions()) {
      int score = scoreByKey.getOrDefault(dim.getKey(), 0);
      dimensionScores.add(TopicDimensionScore.builder()
          .key(dim.getKey())
          .label(dim.getLabel())
          .weight(dim.getWeight())
          .score(score)
          .reason(reasonByKey.getOrDefault(dim.getKey(), ""))
          .build());
    }
    int writingScore = ScoringRubricSupport.computeWritingScore(rubric, scoreByKey);
    String editorReason = StrUtil.trim(StrUtil.nullToEmpty(json.getStr("editorReason")));
    if (StrUtil.isBlank(editorReason)) {
      editorReason = "已按主题量表完成 AI 评分";
    }
    String recommendUse = normalizeRecommendUse(json.getStr("recommendUse"));
    String riskNote = StrUtil.trim(StrUtil.nullToEmpty(json.getStr("riskNote")));
    return TopicWritingScoreApplyRequest.builder()
        .topicId(topicId)
        .writingScore(writingScore)
        .scoreReason(editorReason)
        .dimensions(dimensionScores)
        .rubricVersion(rubric.getVersion())
        .rubricSnapshot(snapshot)
        .recommendUse(recommendUse)
        .riskNote(riskNote)
        .build();
  }

  private static TopicWritingScoreApplyRequest failedApply(
      String topicId,
      List<TopicDimensionScore> snapshot,
      Integer rubricVersion,
      String reason) {
    List<TopicDimensionScore> zeros = new ArrayList<>();
    for (TopicDimensionScore dim : snapshot) {
      zeros.add(TopicDimensionScore.builder()
          .key(dim.getKey())
          .label(dim.getLabel())
          .weight(dim.getWeight())
          .score(0)
          .reason("评分失败")
          .build());
    }
    return TopicWritingScoreApplyRequest.builder()
        .topicId(topicId)
        .writingScore(0)
        .scoreReason(StrUtil.blankToDefault(reason, "AI 评分失败"))
        .dimensions(zeros)
        .rubricVersion(rubricVersion)
        .rubricSnapshot(snapshot)
        .recommendUse("skip")
        .riskNote("")
        .build();
  }

  private String buildUserPrompt(
      ScoringRubricView rubric, String themeContext, HotNewsTopicView topic) {
    StringBuilder dims = new StringBuilder();
    for (ScoringRubricDimensionView dim : rubric.getDimensions()) {
      dims.append("- key=")
          .append(dim.getKey())
          .append(" label=")
          .append(dim.getLabel())
          .append(" weight=")
          .append(dim.getWeight())
          .append("% description=")
          .append(dim.getDescription())
          .append('\n');
    }
    return """
        主题上下文：
        %s

        评分量表指引：%s
        维度：
        %s
        候选选题：
        标题：%s
        摘要：%s
        来源数：%s
        来源列表（请据此判断可信度与一手性）：
        %s
        请按量表逐维打分。
        """.formatted(
        StrUtil.blankToDefault(themeContext, "无"),
        StrUtil.blankToDefault(rubric.getScoreGuidance(), "无"),
        dims,
        StrUtil.blankToDefault(topic.getTitle(), ""),
        StrUtil.blankToDefault(topic.getSummary(), ""),
        topic.getSourceCount() == null ? "0" : String.valueOf(topic.getSourceCount()),
        formatSourceLines(topic.getId()));
  }

  /**
   * 组装选题来源 URL/域名摘要，供 AI 判断可信度。
   *
   * @param topicId 选题 ID
   * @return 多行文本；无来源时返回「无」
   */
  private String formatSourceLines(String topicId) {
    if (StrUtil.isBlank(topicId)) {
      return "无";
    }
    try {
      List<HotNewsTopicSourceView> sources = hotNews.listTopicSources(topicId);
      if (CollUtil.isEmpty(sources)) {
        return "无";
      }
      StringBuilder lines = new StringBuilder();
      int index = 0;
      for (HotNewsTopicSourceView source : sources) {
        if (source == null || StrUtil.isBlank(source.getUrl())) {
          continue;
        }
        index++;
        if (index > 12) {
          lines.append("- …其余来源已省略\n");
          break;
        }
        lines.append("- ")
            .append(StrUtil.blankToDefault(source.getSiteLabel(), hostOf(source.getUrl())))
            .append(" | ")
            .append(source.getUrl());
        if (StrUtil.isNotBlank(source.getTitle())) {
          lines.append(" | ").append(StrUtil.maxLength(source.getTitle(), 80));
        }
        lines.append('\n');
      }
      return lines.length() == 0 ? "无" : lines.toString().trim();
    } catch (Exception exception) {
      log.warn("读取选题来源失败 topicId={} err={}", topicId, exception.getMessage());
      return "无";
    }
  }

  /**
   * 从 URL 提取 host，失败时返回原串截断。
   *
   * @param url 链接
   * @return 域名或短 URL
   */
  private static String hostOf(String url) {
    try {
      String host = java.net.URI.create(url).getHost();
      return StrUtil.blankToDefault(host, StrUtil.maxLength(url, 40));
    } catch (Exception ignored) {
      return StrUtil.maxLength(url, 40);
    }
  }

  private ScoringRubricView loadRubric(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return null;
    }
    try {
      ContentThemeView theme = content.getTheme(themeId);
      if (theme == null) {
        return null;
      }
      Map<String, Object> briefing = readJsonMap(theme.getBriefingJson());
      ScoringRubricView fromBriefing = ScoringRubricSupport.readFrom(briefing);
      if (fromBriefing != null) {
        return fromBriefing;
      }
      return ScoringRubricSupport.readFrom(readJsonMap(theme.getDefaultsJson()));
    } catch (Exception exception) {
      log.warn("读取主题评分量表失败 themeId={} err={}", themeId, exception.getMessage());
      return null;
    }
  }

  private String resolveThemeContext(String themeId) {
    try {
      ContentThemeView theme = content.getTheme(themeId);
      if (theme == null) {
        return "";
      }
      Map<String, Object> briefing = readJsonMap(theme.getBriefingJson());
      if (briefing.isEmpty()) {
        briefing = readJsonMap(theme.getDefaultsJson());
      }
      return HotNewsThemeBriefingSupport.themeContextPrompt(briefing, theme.getName());
    } catch (Exception ignored) {
      return "";
    }
  }

  private static List<TopicDimensionScore> snapshotDims(ScoringRubricView rubric) {
    List<TopicDimensionScore> out = new ArrayList<>();
    for (ScoringRubricDimensionView dim : rubric.getDimensions()) {
      out.add(TopicDimensionScore.builder()
          .key(dim.getKey())
          .label(dim.getLabel())
          .weight(dim.getWeight())
          .score(null)
          .reason(dim.getDescription())
          .build());
    }
    return out;
  }

  private static String normalizeRecommendUse(String raw) {
    String value = StrUtil.trim(StrUtil.nullToEmpty(raw)).toLowerCase(Locale.ROOT);
    return switch (value) {
      case "lead", "brief", "watch", "skip" -> value;
      default -> "watch";
    };
  }

  private static Map<String, Object> readJsonMap(String json) {
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
      // ignore
    }
    return Map.of();
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
}
