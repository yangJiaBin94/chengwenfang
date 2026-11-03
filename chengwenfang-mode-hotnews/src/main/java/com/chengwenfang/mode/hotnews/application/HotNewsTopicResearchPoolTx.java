package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchConflictEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchRunEntity;
import com.chengwenfang.mode.hotnews.domain.TopicResearchPlanner;
import com.chengwenfang.sourcing.domain.entity.VideoTranscriptEntity;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchItemKind;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchConflictMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchRunMapper;
import com.chengwenfang.job.facade.enums.JobStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资料池替换的短事务：先删旧再写新，保证同事务提交/回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsTopicResearchPoolTx {
  private final TopicResearchItemMapper researchItemMapper;
  private final TopicResearchConflictMapper researchConflictMapper;
  private final TopicResearchRunMapper researchRunMapper;
  /** 视频解析共享表：入库时预置 none，或按 URL remount。 */
  private final VideoParseRunService videoParseRuns;
  /** 资料评分明细结构化存储。 */
  private final ResearchItemScoreDetailsStore scoreDetailsStore;

  /**
   * 原子替换选题资料池，并回写调研运行终态。
   *
   * @param topicId           选题 ID
   * @param run               调研运行（会被更新 status/finishedAt/errorMessage）
   * @param collected         本次待写入候选（来源 + 网页）
   * @param webCollectedEmpty 网页检索是否为空（用于错误文案）
   * @param hasSourceItems    是否含原始来源候选
   * @param now               统一时间戳
   * @return 写入条数与冲突条数
   */
  @Transactional
  public ReplaceResult replacePool(
      String topicId,
      TopicResearchRunEntity run,
      List<ResearchCandidate> collected,
      boolean webCollectedEmpty,
      boolean hasSourceItems,
      LocalDateTime now) {
    // 手动添加的素材在自动补充时保留，避免被整池替换清掉
    List<TopicResearchItemEntity> existingItems = researchItemMapper.selectList(
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .eq(TopicResearchItemEntity::getTopicId, topicId));
    List<TopicResearchItemEntity> manualItems = new ArrayList<>();
    List<TopicResearchItemEntity> autoItems = new ArrayList<>();
    Set<String> manualUrlKeys = new LinkedHashSet<>();
    for (TopicResearchItemEntity existing : existingItems) {
      if (existing == null) {
        continue;
      }
      if (ResearchItemKind.from(existing.getKind()).isManual()) {
        manualItems.add(existing);
        String key = TopicResearchPlanner.normalizeUrl(existing.getUrl());
        if (StrUtil.isNotBlank(key)) {
          manualUrlKeys.add(key);
        }
      } else {
        autoItems.add(existing);
      }
    }

    // 删除前按 URL 收集可复用的解析行与摘要，并记下全部旧 transcript 供孤儿清理
    RemountPlan remountPlan = collectRemountSlots(autoItems);
    Map<String, RemountSlot> remountByUrl = remountPlan.slotsByUrl();

    int removedConflicts = researchConflictMapper.delete(new LambdaQueryWrapper<TopicResearchConflictEntity>()
        .eq(TopicResearchConflictEntity::getTopicId, topicId));
    int removedItems = researchItemMapper.delete(new LambdaQueryWrapper<TopicResearchItemEntity>()
        .eq(TopicResearchItemEntity::getTopicId, topicId)
        .ne(TopicResearchItemEntity::getKind, ResearchItemKind.MANUAL.getValue()));
    log.info("资料池事务清空 topicId={} removedItems={} keptManual={} removedConflicts={} remountCandidates={}",
        topicId, removedItems, manualItems.size(), removedConflicts, remountByUrl.size());

    int itemCount = 0;
    int remounted = 0;
    Set<String> usedTranscriptIds = new HashSet<>();
    Map<String, List<ResearchCandidate>> byTitle = new LinkedHashMap<>();
    List<ResearchCandidate> safe = collected == null ? List.of() : collected;
    for (ResearchCandidate candidate : safe) {
      String urlKey = TopicResearchPlanner.normalizeUrl(candidate.getUrl());
      // 与手动素材同链则跳过，避免重复
      if (StrUtil.isNotBlank(urlKey) && manualUrlKeys.contains(urlKey)) {
        continue;
      }
      TopicResearchItemEntity item = new TopicResearchItemEntity();
      item.setId(IdsUtil.newId());
      item.setRunId(run.getId());
      item.setTopicId(topicId);
      item.setTitle(StrUtil.isBlank(candidate.getTitle())
          ? "未命名" : candidate.getTitle());
      item.setUrl(candidate.getUrl());
      String itemSummary = candidate.getSummary() == null ? "" : candidate.getSummary();
      item.setSummary(itemSummary.length() > 4000 ? itemSummary.substring(0, 4000) : itemSummary);
      item.setScore(candidate.getScore());
      item.setKind(candidate.getKind() == null
          ? ResearchItemKind.WEB.getValue() : candidate.getKind().getValue());
      item.setHasScoreDetails(false);
      // 视频素材写入时长；解析状态 sole-write 共享表
      boolean video = ResearchContentClassifier.isVideo(candidate.getUrl(), item.getTitle());
      if (video) {
        item.setVideoDurationSeconds(candidate.getVideoDurationSeconds());
      }
      // 同 URL 已解析成功时保留原摘要，供成文参考
      RemountSlot remount = StrUtil.isBlank(urlKey) ? null : remountByUrl.get(urlKey);
      if (video && remount != null && remount.reusable() && StrUtil.isNotBlank(remount.summary())) {
        String kept = remount.summary();
        item.setSummary(kept.length() > 4000 ? kept.substring(0, 4000) : kept);
        if (item.getVideoDurationSeconds() == null && remount.durationSeconds() != null) {
          item.setVideoDurationSeconds(remount.durationSeconds());
        }
      }
      item.setCreatedAt(now);
      researchItemMapper.insert(item);
      scoreDetailsStore.write(item.getId(), candidate.getScoreDetails());
      if (video) {
        if (remount != null && remount.reusable()
            && remount.transcript() != null
            && !usedTranscriptIds.contains(remount.transcript().getId())) {
          videoParseRuns.rebindResearchItem(
              remount.transcript(),
              item.getId(),
              item.getUrl(),
              item.getVideoDurationSeconds());
          usedTranscriptIds.add(remount.transcript().getId());
          remounted++;
        } else {
          videoParseRuns.ensureNone(
              VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM,
              item.getId(),
              item.getUrl(),
              null,
              item.getVideoDurationSeconds());
        }
      }
      itemCount++;
      byTitle.computeIfAbsent(candidate.normalizedTitle(), ignored -> new ArrayList<>()).add(candidate);
    }

    // 未再绑定的旧解析行清理（含同 URL 被挤掉的槽），避免悬挂
    Set<String> orphanIds = new HashSet<>();
    for (String transcriptId : remountPlan.allTranscriptIds()) {
      if (StrUtil.isNotBlank(transcriptId) && !usedTranscriptIds.contains(transcriptId)) {
        orphanIds.add(transcriptId);
      }
    }
    if (!orphanIds.isEmpty()) {
      videoParseRuns.deleteByIds(orphanIds);
    }

    int conflictCount = 0;
    for (Map.Entry<String, List<ResearchCandidate>> entry : byTitle.entrySet()) {
      if (StrUtil.isBlank(entry.getKey()) || entry.getValue().size() < 2) {
        continue;
      }
      TopicResearchConflictEntity conflict = new TopicResearchConflictEntity();
      conflict.setId(IdsUtil.newId());
      conflict.setRunId(run.getId());
      conflict.setTopicId(topicId);
      conflict.setClaim(entry.getValue().get(0).getTitle());
      conflict.setDetail("同标题出现在 " + entry.getValue().size() + " 条资料中，需人工核对");
      conflict.setCreatedAt(now);
      researchConflictMapper.insert(conflict);
      conflictCount++;
    }

    int totalVisible = itemCount + manualItems.size();
    String status = totalVisible > 0 ? JobStatus.SUCCEEDED.getValue() : "partial";
    run.setStatus(status);
    run.setFinishedAt(now);
    if (totalVisible <= 0) {
      run.setErrorMessage("未整理到资料，请检查选题来源与 SearXNG 是否可从本机访问");
    } else if (webCollectedEmpty && hasSourceItems) {
      run.setErrorMessage("未检索到可补充网页资料（已保留选题原始来源；请检查 SearXNG）");
    } else {
      run.setErrorMessage(null);
    }
    researchRunMapper.updateById(run);
    log.info(
        "资料池事务写入完成 topicId={} itemCount={} remounted={} orphans={} conflictCount={} status={}",
        topicId, itemCount, remounted, orphanIds.size(), conflictCount, status);
    return new ReplaceResult(itemCount, conflictCount, status);
  }

  /**
   * 收集自动资料中可按 URL remount 的解析槽；同 URL 优先 succeeded。
   * <p>同时收集全部旧 transcript id，供被挤掉的槽做孤儿清理。
   *
   * @param autoItems 非手动条目
   * @return remount 计划
   */
  private RemountPlan collectRemountSlots(List<TopicResearchItemEntity> autoItems) {
    Map<String, RemountSlot> slotsByUrl = new LinkedHashMap<>();
    Set<String> allTranscriptIds = new HashSet<>();
    if (autoItems == null || autoItems.isEmpty()) {
      return new RemountPlan(slotsByUrl, allTranscriptIds);
    }
    List<String> videoItemIds = new ArrayList<>();
    Map<String, TopicResearchItemEntity> itemById = new LinkedHashMap<>();
    for (TopicResearchItemEntity item : autoItems) {
      if (item == null || StrUtil.isBlank(item.getId())) {
        continue;
      }
      if (!ResearchContentClassifier.isVideo(item.getUrl(), item.getTitle())) {
        continue;
      }
      videoItemIds.add(item.getId());
      itemById.put(item.getId(), item);
    }
    if (videoItemIds.isEmpty()) {
      return new RemountPlan(slotsByUrl, allTranscriptIds);
    }
    Map<String, VideoTranscriptEntity> byOwner =
        videoParseRuns.mapByOwnerIds(VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM, videoItemIds);
    for (Map.Entry<String, TopicResearchItemEntity> entry : itemById.entrySet()) {
      TopicResearchItemEntity item = entry.getValue();
      VideoTranscriptEntity transcript = byOwner.get(entry.getKey());
      if (transcript == null) {
        continue;
      }
      if (StrUtil.isNotBlank(transcript.getId())) {
        allTranscriptIds.add(transcript.getId());
      }
      String urlKey = TopicResearchPlanner.normalizeUrl(item.getUrl());
      if (StrUtil.isBlank(urlKey)) {
        continue;
      }
      RemountSlot next = new RemountSlot(
          transcript,
          StrUtil.nullToEmpty(item.getSummary()),
          item.getVideoDurationSeconds() != null
              ? item.getVideoDurationSeconds()
              : transcript.getDurationSeconds());
      RemountSlot prev = slotsByUrl.get(urlKey);
      if (prev == null || preferRemount(next, prev)) {
        slotsByUrl.put(urlKey, next);
      }
    }
    return new RemountPlan(slotsByUrl, allTranscriptIds);
  }

  /**
   * 整池替换前的 remount 计划。
   *
   * @param slotsByUrl        normalizeUrl → 优选槽
   * @param allTranscriptIds  旧自动视频关联的全部 transcript id
   */
  private record RemountPlan(
      Map<String, RemountSlot> slotsByUrl,
      Set<String> allTranscriptIds) {}

  /**
   * 同 URL 多槽时优先可复用且 succeeded 的。
   *
   * @param candidate 候选
   * @param current   已选
   * @return 是否用候选替换
   */
  private static boolean preferRemount(RemountSlot candidate, RemountSlot current) {
    if (current == null) {
      return true;
    }
    if (!current.reusable() && candidate.reusable()) {
      return true;
    }
    if (candidate.reusable() && current.reusable()) {
      return VideoParseStatus.from(candidate.transcript().getStatus()).isSucceeded()
          && !VideoParseStatus.from(current.transcript().getStatus()).isSucceeded();
    }
    return false;
  }

  /**
   * 同 URL 解析复用槽。
   *
   * @param transcript      共享行
   * @param summary         原条目摘要
   * @param durationSeconds 时长
   */
  private record RemountSlot(
      VideoTranscriptEntity transcript,
      String summary,
      Integer durationSeconds) {
    /**
     * 非 none 的解析状态可改绑复用。
     *
     * @return 可复用时 true
     */
    boolean reusable() {
      if (transcript == null) {
        return false;
      }
      return VideoParseStatus.from(transcript.getStatus()) != VideoParseStatus.NONE;
    }
  }

  /**
   * 资料池替换事务结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class ReplaceResult {
    /** 写入条目数。 */
    private final int itemCount;
    /** 冲突条数。 */
    private final int conflictCount;
    /** 落库状态。 */
    private final String status;
  }
}
