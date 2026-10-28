package com.chengwenfang.mode.hotnews.application.videoparse;

import com.chengwenfang.sourcing.application.videoparse.VideoParseOwnerStrategy;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.sourcing.application.VideoParseErrorMessages;
import com.chengwenfang.mode.hotnews.application.VideoParseRunService;
import com.chengwenfang.mode.hotnews.application.VideoTranscriptService;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 资料池条目视频解析策略：目标加载、时长优先排队、单条解析与失败标记。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopicResearchItemVideoParseStrategy
    implements VideoParseOwnerStrategy<TopicResearchItemEntity> {

  /** 资料池条目 Mapper。 */
  private final TopicResearchItemMapper researchItemMapper;
  /** 字幕/时长解析。 */
  private final VideoTranscriptService videoTranscriptService;
  /** 解析状态共享表。 */
  private final VideoParseRunService videoParseRuns;

  @Override
  public String ownerType() {
    return VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM;
  }

  @Override
  public boolean supports(String ownerType) {
    return ownerType().equals(ownerType);
  }

  /**
   * 解析视频目标条目：指定 ID 或选题下全部未成功视频。
   *
   * @param topicId 选题 ID
   * @param itemIds 可选条目 ID
   * @return 待解析列表
   */
  public List<TopicResearchItemEntity> loadParseTargets(String topicId, List<String> itemIds) {
    if (itemIds != null && !itemIds.isEmpty()) {
      List<TopicResearchItemEntity> list = new ArrayList<>();
      for (String itemId : itemIds) {
        TopicResearchItemEntity item = researchItemMapper.selectById(itemId);
        if (item != null && topicId.equals(item.getTopicId())) {
          list.add(item);
        }
      }
      return list;
    }
    List<TopicResearchItemEntity> all = researchItemMapper.selectList(
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .eq(TopicResearchItemEntity::getTopicId, topicId)
            .orderByDesc(TopicResearchItemEntity::getScore));
    videoParseRuns.applyToResearchItems(all);
    List<TopicResearchItemEntity> videos = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (TopicResearchItemEntity item : all) {
      if (item == null || StrUtil.isBlank(item.getUrl())) {
        continue;
      }
      if (!ResearchContentClassifier.isVideo(item.getUrl(), item.getTitle())) {
        continue;
      }
      if (!seen.add(item.getId())) {
        continue;
      }
      VideoParseStatus status = VideoParseStatus.from(item.getVideoParseStatus());
      if (!status.needsParse()) {
        continue;
      }
      videos.add(item);
    }
    return videos;
  }

  /**
   * 按已落库时长降序重排；未知时长视为 0，排在队尾。
   * <p>启动阶段不做 yt-dlp 同步探测：探测单条可卡数十秒，会让整批长时间停在「等待线程池调度」。
   *
   * @param items 待解析条目（原地排序）
   */
  public void prioritizeByDuration(List<TopicResearchItemEntity> items) {
    if (items == null || items.size() <= 1) {
      return;
    }
    items.sort((left, right) -> Integer.compare(
        durationSecondsOrZero(right),
        durationSecondsOrZero(left)));
    TopicResearchItemEntity first = items.get(0);
    log.info(
        "资料池视频解析排队（时长优先） count={} firstTitle={} firstSec={}",
        items.size(),
        StrUtil.blankToDefault(first.getTitle(), first.getUrl()),
        durationSecondsOrZero(first));
  }

  /**
   * 按主键重载并水合解析状态；不存在返回 null。
   *
   * @param ownerId 条目 ID
   * @return 实体；不存在时 null
   */
  @Override
  public TopicResearchItemEntity reload(String ownerId) {
    if (StrUtil.isBlank(ownerId)) {
      return null;
    }
    TopicResearchItemEntity item = researchItemMapper.selectById(ownerId);
    videoParseRuns.applyToResearchItem(item);
    return item;
  }

  /**
   * 执行单条资料池视频解析。
   *
   * @param item         条目（须含 id/url）
   * @param fromStep     起始步骤
   * @param progressHook 进度回调
   */
  public void parseOne(
      TopicResearchItemEntity item,
      VideoParseStep fromStep,
      BiConsumer<String, String> progressHook) {
    videoTranscriptService.parseItem(item, fromStep, progressHook);
  }

  /**
   * @param item 已水合条目
   * @return 是否解析成功
   */
  public boolean isSucceeded(TopicResearchItemEntity item) {
    return item != null && VideoParseStatus.from(item.getVideoParseStatus()).isSucceeded();
  }

  /**
   * 资料池口播原文：内存优先，否则读共享表。
   *
   * @param item 资料池条目
   * @return 原文；可空
   */
  public String resolveRawText(TopicResearchItemEntity item) {
    if (item == null) {
      return null;
    }
    if (StrUtil.isNotBlank(item.getVideoRawText())) {
      return item.getVideoRawText();
    }
    String raw = videoParseRuns.findRawText(ownerType(), item.getId());
    item.setVideoRawText(raw);
    return raw;
  }

  @Override
  public void markFailed(String ownerId, String errorMessage) {
    if (StrUtil.isBlank(ownerId)) {
      return;
    }
    try {
      TopicResearchItemEntity item = researchItemMapper.selectById(ownerId);
      if (item == null) {
        return;
      }
      videoParseRuns.applyToResearchItem(item);
      VideoParseStatus status = VideoParseStatus.from(item.getVideoParseStatus());
      if (status.isSucceeded()) {
        return;
      }
      VideoParseStep step = VideoParseStep.from(item.getVideoParseStep());
      item.setVideoParseStatus(VideoParseStatus.FAILED.getValue());
      item.setVideoParseError(StrUtil.maxLength(
          VideoParseErrorMessages.humanize(errorMessage, step), 500));
      // 解析失败状态 sole-write 共享表
      videoParseRuns.upsertFromResearchItem(item);
    } catch (Exception ignored) {
      log.warn("标记视频解析失败状态时出错 itemId={}", ownerId);
    }
  }

  /**
   * 条目已落库时长；缺失视为 0。
   *
   * @param item 资料池条目
   * @return 秒数；未知为 0
   */
  private static int durationSecondsOrZero(TopicResearchItemEntity item) {
    if (item == null || item.getVideoDurationSeconds() == null || item.getVideoDurationSeconds() <= 0) {
      return 0;
    }
    return item.getVideoDurationSeconds();
  }
}
