package com.workbench.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.mode.hotnews.facade.dto.request.RadarSurvivorPersistRequest;
import com.workbench.mode.hotnews.facade.dto.view.RadarPersistClusterResult;
import com.workbench.infra.config.WorkbenchProperties;
import com.workbench.job.facade.JobFacade;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 幸存者落库、成簇，并用 Skill 合并簇摘要。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistClusterRadarNodeHandler implements RadarCollectNodeHandler {
  private final HotNewsFacade hotNews;
  private final RadarCollectAiSupport aiSupport;
  private final JobFacade jobs;
  private final WorkbenchProperties properties;

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.PERSIST_CLUSTER;
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
    String stepKey = HotNewsRadarCollectNodeType.PERSIST_CLUSTER.getValue();
    List<RadarSurvivorPersistRequest> survivors = new ArrayList<>();
    for (RadarCollectCandidate candidate : session.survivors()) {
      // 落库前丢弃原文
      candidate.setRawText(null);
      survivors.add(RadarSurvivorPersistRequest.builder()
          .tempId(candidate.getTempId())
          .channelId(candidate.getChannelId())
          .url(candidate.getUrl())
          .title(candidate.getTitle())
          .author(candidate.getAuthor())
          .publishedAt(candidate.getPublishedAt())
          .factBrief(candidate.getFactBrief())
          .mediaType(candidate.getMediaType())
          .siteLabel(candidate.getSiteLabel())
          .build());
    }
    int inputCount = survivors.size();
    reportProgress(jobId, stepKey, 80, "落库成簇：参与 " + inputCount + " 条…");
    RadarCollectLeaseSupport.renew(lightState);
    RadarPersistClusterResult result = hotNews.persistAndClusterRadarSurvivors(
        session.getThemeId(), session.getRadarBatchId(), survivors);
    for (RadarCollectCandidate candidate : session.getCandidates()) {
      if (candidate == null || StrUtil.isBlank(candidate.getTempId())) {
        continue;
      }
      String sourceId = result.getSourceIdByTempId().get(candidate.getTempId());
      if (sourceId != null) {
        candidate.setPersistedSourceId(sourceId);
      }
    }
    Map<String, String> summaries = new LinkedHashMap<>();
    for (String topicId : result.getTopicIds()) {
      List<String> briefs = result.getMemberBriefsByTopicId().getOrDefault(topicId, List.of());
      String title = result.getTitleByTopicId().getOrDefault(topicId, "");
      String merged = aiSupport.mergeClusterSummary(skillBindings, title, briefs);
      if (StrUtil.isBlank(merged) && !briefs.isEmpty()) {
        merged = String.join("；", briefs);
        if (merged.length() > 1200) {
          merged = merged.substring(0, 1200);
        }
      }
      if (StrUtil.isNotBlank(merged)) {
        summaries.put(topicId, merged);
      }
    }
    hotNews.updateRadarClusterSummaries(summaries);
    session.setInsertedCount(result.getInserted());
    session.setTopicCount(result.getTopicCount());
    int topicCount = result.getTopicCount();
    String note = "落库成簇：参与 "
        + inputCount
        + " 条，合并成簇 "
        + topicCount
        + " 个";
    lightState.put(RadarCollectGraphStateKeys.INSERTED_COUNT, result.getInserted());
    lightState.put(RadarCollectGraphStateKeys.TOPIC_COUNT, topicCount);
    lightState.put(RadarCollectGraphStateKeys.SURVIVOR_COUNT, inputCount);
    lightState.put(RadarCollectGraphStateKeys.PERSIST_INPUT_COUNT, inputCount);
    lightState.put(RadarCollectGraphStateKeys.PERSIST_NOTE, note);
    reportProgress(jobId, stepKey, 86, note);
    if (StrUtil.isNotBlank(jobId)) {
      try {
        jobs.renewLease(jobId, properties.getWorkerLeaseSeconds());
      } catch (Exception exception) {
        log.warn("落库成簇租约续期失败 jobId={} err={}", jobId, exception.getMessage());
      }
    }
    log.info(
        "雷达落库成簇完成 input={} inserted={} topics={}",
        inputCount,
        result.getInserted(),
        topicCount);
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
      jobs.updateProgress(jobId, stepKey, Math.min(90, Math.max(1, progress)), message);
    } catch (Exception exception) {
      log.warn("落库成簇进度上报失败 jobId={} err={}", jobId, exception.getMessage());
    }
  }

  /**
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String stringVal(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
