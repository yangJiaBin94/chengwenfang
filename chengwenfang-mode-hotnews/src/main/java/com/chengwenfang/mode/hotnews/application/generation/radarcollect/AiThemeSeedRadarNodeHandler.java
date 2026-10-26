package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AiThemeSeedAiResult;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.HotNewsThemeBriefingSupport;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.mode.hotnews.domain.TopicMathUtil;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsTopicOriginKinds;
import com.chengwenfang.mode.hotnews.facade.dto.request.AiThemeSeedPersistRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按主题问卷生成 AI 主题种子素材；本批不足 {@link TopicMathUtil#DAILY_TOPIC_LIMIT} 时补齐。
 * <p>生成结果做标题去重自检；不足或撞题时最多再补生成
 * {@link AiThemeSeedDedupeSupport#MAX_REFILL_RETRIES} 次，仍不足则放弃。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiThemeSeedRadarNodeHandler implements RadarCollectNodeHandler {
  /** 内容主题门面。 */
  private final ContentFacade content;
  /** 热点门面。 */
  private final HotNewsFacade hotNews;
  /** 雷达 AI 支持。 */
  private final RadarCollectAiSupport aiSupport;
  /** 任务门面。 */
  private final JobFacade jobs;

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.AI_THEME_SEED;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    String jobId = StrUtil.blankToDefault(
        stringVal(lightState.get(RadarCollectGraphStateKeys.JOB_ID)),
        session.getFetchRunId());
    String stepKey = HotNewsRadarCollectNodeType.AI_THEME_SEED.getValue();
    reportProgress(jobId, stepKey, 88, "正在生成 AI 主题素材…");
    RadarCollectLeaseSupport.renew(lightState);

    String themeId = session.getThemeId();
    String radarBatchId = session.getRadarBatchId();
    if (StrUtil.isBlank(themeId) || StrUtil.isBlank(radarBatchId)) {
      String note = "跳过 AI 主题素材：缺少 themeId 或 radarBatchId";
      lightState.put(RadarCollectGraphStateKeys.AI_THEME_SEED_NOTE, note);
      reportProgress(jobId, stepKey, 89, note);
      log.warn(note);
      return;
    }

    List<HotNewsTopicView> batchTopics = hotNews.listRadarBatchTopics(themeId, radarBatchId);
    int batchCount = batchTopics.size();
    int existingAiSeeds = countAiThemeSeeds(batchTopics);
    int need = TopicMathUtil.aiThemeSeedNeed(batchCount, existingAiSeeds);
    if (need <= 0) {
      String note = "本批选题已满 "
          + TopicMathUtil.DAILY_TOPIC_LIMIT
          + " 且已有 AI 主题素材，跳过补全";
      session.setTopicCount(batchCount);
      lightState.put(RadarCollectGraphStateKeys.TOPIC_COUNT, batchCount);
      lightState.put(RadarCollectGraphStateKeys.AI_THEME_SEED_NOTE, note);
      reportProgress(jobId, stepKey, 90, note);
      log.info("{} themeId={} batch={} n={}", note, themeId, radarBatchId, batchCount);
      return;
    }

    // 已占用标题（本批已有）；后续收下的新题也会并入，供补生成避重
    Set<String> reservedKeys = AiThemeSeedDedupeSupport.toKeySet(existingTitles(batchTopics));
    List<AiThemeSeedAiResult> accepted = new ArrayList<>();
    int maxAttempts = 1 + AiThemeSeedDedupeSupport.MAX_REFILL_RETRIES;
    int refillUsed = 0;

    for (int attempt = 0; attempt < maxAttempts && accepted.size() < need; attempt++) {
      int remaining = need - accepted.size();
      if (attempt > 0) {
        refillUsed = attempt;
        reportProgress(
            jobId,
            stepKey,
            88,
            "AI 主题素材去重后不足，第 " + attempt + " 次补生成（还需 " + remaining + " 条）…");
        RadarCollectLeaseSupport.renew(lightState);
      } else {
        reportProgress(
            jobId,
            stepKey,
            88,
            "正在生成 AI 主题素材（需 " + remaining + " 条）…");
      }

      String userPrompt = buildThemeBriefingPrompt(themeId, remaining, reservedKeys);
      if (StrUtil.isBlank(userPrompt)) {
        String note = "跳过 AI 主题素材：主题问卷为空";
        lightState.put(RadarCollectGraphStateKeys.AI_THEME_SEED_NOTE, note);
        reportProgress(jobId, stepKey, 89, note);
        log.warn("{} themeId={}", note, themeId);
        return;
      }

      List<AiThemeSeedAiResult> generated =
          aiSupport.generateAiThemeSeeds(skillBindings, userPrompt, remaining);
      List<AiThemeSeedAiResult> unique =
          AiThemeSeedDedupeSupport.takeUnique(generated, reservedKeys, remaining);
      if (unique.isEmpty()) {
        log.info(
            "AI 主题素材本轮无新增唯一选题 themeId={} batch={} attempt={} remaining={}",
            themeId,
            radarBatchId,
            attempt,
            remaining);
        continue;
      }
      for (AiThemeSeedAiResult item : unique) {
        accepted.add(item);
        reservedKeys.add(AiThemeSeedDedupeSupport.titleKey(item.getTitle()));
      }
    }

    if (accepted.isEmpty()) {
      String note = "跳过 AI 主题素材：模型未返回可用且不重复的选题";
      lightState.put(RadarCollectGraphStateKeys.AI_THEME_SEED_NOTE, note);
      reportProgress(jobId, stepKey, 89, note);
      log.warn("{} themeId={} batch={} need={}", note, themeId, radarBatchId, need);
      return;
    }

    List<String> persistedIds = new ArrayList<>();
    for (AiThemeSeedAiResult item : accepted) {
      HotNewsTopicView topic = hotNews.persistAiThemeSeedTopic(
          themeId,
          radarBatchId,
          AiThemeSeedPersistRequest.builder()
              .title(item.getTitle())
              .summary(item.getSummary())
              .signals(item.getSignals())
              .angle(item.getAngle())
              .build());
      if (topic != null && StrUtil.isNotBlank(topic.getId())) {
        persistedIds.add(topic.getId());
      }
    }
    if (persistedIds.isEmpty()) {
      String note = "跳过 AI 主题素材：落库失败";
      lightState.put(RadarCollectGraphStateKeys.AI_THEME_SEED_NOTE, note);
      reportProgress(jobId, stepKey, 89, note);
      log.warn("{} themeId={} batch={}", note, themeId, radarBatchId);
      return;
    }

    int topicCount = hotNews.listRadarBatchTopics(themeId, radarBatchId).size();
    session.setTopicCount(topicCount);
    lightState.put(RadarCollectGraphStateKeys.TOPIC_COUNT, topicCount);
    lightState.put(RadarCollectGraphStateKeys.AI_THEME_SEED_TOPIC_ID, persistedIds.get(0));
    boolean incomplete = accepted.size() < need;
    String note;
    if (incomplete) {
      note = "AI 主题素材去重补全未满：收下 "
          + persistedIds.size()
          + "/"
          + need
          + "（补生成 "
          + refillUsed
          + "/"
          + AiThemeSeedDedupeSupport.MAX_REFILL_RETRIES
          + " 次后放弃；本批共 "
          + topicCount
          + " 条）";
    } else {
      note = "已补全 AI 主题素材 "
          + persistedIds.size()
          + "/"
          + need
          + (refillUsed > 0
              ? "（含补生成 " + refillUsed + " 次）"
              : "")
          + "（本批共 "
          + topicCount
          + " 条，目标 "
          + TopicMathUtil.DAILY_TOPIC_LIMIT
          + "）";
    }
    lightState.put(RadarCollectGraphStateKeys.AI_THEME_SEED_NOTE, note);
    reportProgress(jobId, stepKey, 90, note);
    log.info(
        "AI 主题素材节点完成 themeId={} batch={} added={} need={} refill={} topicCount={}",
        themeId,
        radarBatchId,
        persistedIds.size(),
        need,
        refillUsed,
        topicCount);
  }

  /**
   * 统计本批 AI 主题种子条数。
   *
   * @param topics 本批选题
   * @return 种子数
   */
  private static int countAiThemeSeeds(List<HotNewsTopicView> topics) {
    if (topics == null || topics.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (HotNewsTopicView topic : topics) {
      if (topic != null && HotNewsTopicOriginKinds.isAiThemeSeed(topic.getOriginKind())) {
        count++;
      }
    }
    return count;
  }

  /**
   * 从主题问卷拼装用户提示。
   *
   * @param themeId     主题 ID
   * @param need        需要产出的条数
   * @param avoidKeys   已占用标题键（请勿重复）
   * @return 用户提示；无有效信息时空串
   */
  private String buildThemeBriefingPrompt(
      String themeId, int need, Collection<String> avoidKeys) {
    try {
      ContentThemeView theme = content.getTheme(themeId);
      if (theme == null) {
        return "";
      }
      Map<String, Object> briefing = readBriefing(theme.getBriefingJson());
      String name = StrUtil.firstNonBlank(
          HotNewsThemeBriefingSupport.themeName(briefing),
          theme.getName(),
          "未命名主题");
      Map<String, Object> naming = nestedMap(briefing, "naming");
      String description = StrUtil.firstNonBlank(
          trimOrNull(naming.get("description")),
          theme.getDescription());
      List<String> searchQueries = HotNewsThemeBriefingSupport.searchQueries(briefing);
      List<String> mustWatch = HotNewsThemeBriefingSupport.mustWatch(briefing);
      Map<String, Object> valueFilters = nestedMap(briefing, "value_filters");
      List<String> worthWriting = stringList(valueFilters.get("worth_writing"));
      if (worthWriting.isEmpty()) {
        worthWriting = stringList(briefing.get("worth_writing"));
      }
      Map<String, Object> audience = nestedMap(briefing, "audience");
      List<String> readers = stringList(audience.get("readers"));
      if (readers.isEmpty()) {
        readers = stringList(briefing.get("readers"));
      }
      String contentStance = StrUtil.firstNonBlank(
          trimOrNull(audience.get("content_stance")),
          trimOrNull(briefing.get("content_stance")));
      Map<String, Object> scope = nestedMap(briefing, "scope");
      String outOfScope = StrUtil.firstNonBlank(
          trimOrNull(scope.get("out_of_scope")),
          trimOrNull(briefing.get("out_of_scope")));
      StringBuilder builder = new StringBuilder();
      builder.append("【主题问卷】\n");
      builder.append("主题名称：").append(name).append('\n');
      builder.append("主题说明：")
          .append(StrUtil.blankToDefault(description, "（未填写）"))
          .append('\n');
      builder.append("搜索词：")
          .append(searchQueries.isEmpty() ? "（无）" : String.join("、", searchQueries))
          .append('\n');
      builder.append("关注实体：")
          .append(mustWatch.isEmpty() ? "（无）" : String.join("、", mustWatch))
          .append('\n');
      builder.append("值得写的信号：")
          .append(worthWriting.isEmpty() ? "（无）" : String.join("、", worthWriting))
          .append('\n');
      builder.append("主要读者：")
          .append(readers.isEmpty() ? "（无）" : String.join("、", readers))
          .append('\n');
      builder.append("内容姿态：")
          .append(StrUtil.blankToDefault(contentStance, "（未填写）"))
          .append('\n');
      builder.append("明确不做：")
          .append(StrUtil.blankToDefault(outOfScope, "（未填写）"))
          .append('\n');
      String context = HotNewsThemeBriefingSupport.themeContextPrompt(briefing, name);
      if (StrUtil.isNotBlank(context)) {
        builder.append('\n').append(context).append('\n');
      }
      if (avoidKeys != null && !avoidKeys.isEmpty()) {
        builder.append("\n已有选题标题（请勿重复）：")
            .append(String.join("、", avoidKeys))
            .append('\n');
      }
      builder.append("\n请按系统规则输出恰好 ")
          .append(Math.max(1, need))
          .append(" 条主题种子素材 JSON（items 数组，标题互不雷同，且不得与「已有选题标题」重复）。");
      return builder.toString();
    } catch (Exception exception) {
      log.warn("组装 AI 主题素材提示失败 themeId={} err={}", themeId, exception.getMessage());
      return "";
    }
  }

  /**
   * 收集本批已有标题，供避重。
   *
   * @param topics 本批选题
   * @return 标题列表
   */
  private static List<String> existingTitles(List<HotNewsTopicView> topics) {
    if (topics == null || topics.isEmpty()) {
      return List.of();
    }
    List<String> titles = new ArrayList<>();
    for (HotNewsTopicView topic : topics) {
      if (topic == null) {
        continue;
      }
      String title = StrUtil.trim(StrUtil.nullToEmpty(topic.getTitle()));
      if (StrUtil.isNotBlank(title)) {
        titles.add(title);
      }
    }
    return titles;
  }

  /**
   * 上报进度。
   *
   * @param jobId    任务 ID
   * @param stepKey  步骤键
   * @param progress 进度
   * @param message  文案
   */
  private void reportProgress(String jobId, String stepKey, int progress, String message) {
    if (StrUtil.isBlank(jobId)) {
      return;
    }
    try {
      jobs.updateProgress(jobId, stepKey, Math.min(95, Math.max(1, progress)), message);
    } catch (Exception exception) {
      log.warn("AI 主题素材进度上报失败 jobId={} err={}", jobId, exception.getMessage());
    }
  }

  private static Map<String, Object> readBriefing(String json) {
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
      // 问卷损坏时退回空 map
    }
    return Map.of();
  }

  private static Map<String, Object> nestedMap(Map<String, Object> root, String key) {
    if (root == null) {
      return Map.of();
    }
    Object value = root.get(key);
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() != null) {
          out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
      return out;
    }
    return Map.of();
  }

  private static List<String> stringList(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .map(item -> StrUtil.trim(StrUtil.nullToEmpty(item == null ? null : String.valueOf(item))))
        .filter(StrUtil::isNotBlank)
        .toList();
  }

  private static String trimOrNull(Object value) {
    if (value == null) {
      return null;
    }
    String text = StrUtil.trim(String.valueOf(value));
    return StrUtil.isBlank(text) ? null : text;
  }

  private static String stringVal(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
