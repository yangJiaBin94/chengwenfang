package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.mode.hotnews.application.model.ResearchCandidate;
import com.workbench.mode.hotnews.domain.ResearchContentClassifier;
import com.workbench.mode.hotnews.domain.entity.TopicResearchConflictEntity;
import com.workbench.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.workbench.mode.hotnews.domain.entity.TopicResearchRunEntity;
import com.workbench.mode.hotnews.domain.TopicResearchPlanner;
import com.workbench.sourcing.facade.constant.VideoParseOwnerTypes;
import com.workbench.mode.hotnews.facade.enums.ResearchItemKind;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchConflictMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchRunMapper;
import com.workbench.job.facade.enums.JobStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
  /** 视频解析共享表：入库时预置 none。 */
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
    List<TopicResearchItemEntity> manualItems = researchItemMapper.selectList(
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .eq(TopicResearchItemEntity::getTopicId, topicId)
            .eq(TopicResearchItemEntity::getKind, ResearchItemKind.MANUAL.getValue()));
    Set<String> manualUrlKeys = new LinkedHashSet<>();
    for (TopicResearchItemEntity manual : manualItems) {
      String key = TopicResearchPlanner.normalizeUrl(manual.getUrl());
      if (StrUtil.isNotBlank(key)) {
        manualUrlKeys.add(key);
      }
    }

    int removedConflicts = researchConflictMapper.delete(new LambdaQueryWrapper<TopicResearchConflictEntity>()
        .eq(TopicResearchConflictEntity::getTopicId, topicId));
    int removedItems = researchItemMapper.delete(new LambdaQueryWrapper<TopicResearchItemEntity>()
        .eq(TopicResearchItemEntity::getTopicId, topicId)
        .ne(TopicResearchItemEntity::getKind, ResearchItemKind.MANUAL.getValue()));
    log.info("资料池事务清空 topicId={} removedItems={} keptManual={} removedConflicts={}",
        topicId, removedItems, manualItems.size(), removedConflicts);

    int itemCount = 0;
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
      item.setCreatedAt(now);
      researchItemMapper.insert(item);
      scoreDetailsStore.write(item.getId(), candidate.getScoreDetails());
      if (video) {
        videoParseRuns.ensureNone(
            VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM,
            item.getId(),
            item.getUrl(),
            null,
            item.getVideoDurationSeconds());
      }
      itemCount++;
      byTitle.computeIfAbsent(candidate.normalizedTitle(), ignored -> new ArrayList<>()).add(candidate);
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
      conflict.setDetail("同标题出现在 " + entry.getValue().size() + " 条资料中，需交叉核验");
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
    log.info("资料池事务写入完成 topicId={} itemCount={} conflictCount={} status={}",
        topicId, itemCount, conflictCount, status);
    return new ReplaceResult(itemCount, conflictCount, status);
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
