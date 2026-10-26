package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.mode.hotnews.facade.VideoParseSink;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import com.chengwenfang.sourcing.application.VideoParseErrorMessages;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceItemMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 视频解析落库：资料池/信源 Sink 适配与运行态读写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscriptStore {
  /** 资料池条目 Mapper。 */
  private final TopicResearchItemMapper researchItemMapper;
  /** 信源条目 Mapper。 */
  private final SourceItemMapper sourceItemMapper;
  /** 视频解析共享表（raw_text sole-write）。 */
  private final VideoParseRunService videoParseRuns;

  /**
   * 资料池条目可写目标。
   *
   * @param item 资料条目
   * @return 落库 Sink
   */
  public VideoParseSink researchSink(TopicResearchItemEntity item) {
    return new VideoParseSink() {
      @Override
      public String getId() {
        return item.getId();
      }

      @Override
      public String getUrl() {
        return item.getUrl();
      }

      @Override
      public String getTitle() {
        return item.getTitle();
      }

      @Override
      public String getVideoRawText() {
        if (StrUtil.isNotBlank(item.getVideoRawText())) {
          return item.getVideoRawText();
        }
        // 从共享表补齐内存字段
        String raw = videoParseRuns.findRawText(
            VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM, item.getId());
        item.setVideoRawText(raw);
        return raw;
      }

      @Override
      public String getVideoContentKind() {
        return item.getVideoContentKind();
      }

      @Override
      public void setVideoRawText(String videoRawText) {
        item.setVideoRawText(videoRawText);
      }

      @Override
      public void setVideoContentKind(String videoContentKind) {
        item.setVideoContentKind(videoContentKind);
      }

      @Override
      public void setVideoParseStatus(String videoParseStatus) {
        item.setVideoParseStatus(videoParseStatus);
      }

      @Override
      public void setVideoParseStep(String videoParseStep) {
        item.setVideoParseStep(videoParseStep);
      }

      @Override
      public void setVideoParseError(String videoParseError) {
        item.setVideoParseError(videoParseError);
      }

      @Override
      public void setVideoParsedAt(LocalDateTime videoParsedAt) {
        item.setVideoParsedAt(videoParsedAt);
      }

      @Override
      public void setSummary(String summary) {
        item.setSummary(summary);
      }

      @Override
      public void setVideoDurationSeconds(Integer videoDurationSeconds) {
        item.setVideoDurationSeconds(videoDurationSeconds);
      }

      @Override
      public void persist() {
        // 所属表写摘要等；解析状态/原文/时长/类型 sole-write 共享表
        researchItemMapper.updateById(item);
        videoParseRuns.upsertFromResearchItem(item);
      }
    };
  }

  /**
   * 雷达信源条目可写目标。
   *
   * @param item 信源条目
   * @return 落库 Sink
   */
  public VideoParseSink sourceSink(SourceItemEntity item) {
    return new VideoParseSink() {
      @Override
      public String getId() {
        return item.getId();
      }

      @Override
      public String getUrl() {
        return item.getUrl();
      }

      @Override
      public String getTitle() {
        return item.getTitle();
      }

      @Override
      public String getVideoRawText() {
        if (StrUtil.isNotBlank(item.getVideoRawText())) {
          return item.getVideoRawText();
        }
        String raw = videoParseRuns.findRawText(VideoParseOwnerTypes.SOURCE_ITEM, item.getId());
        item.setVideoRawText(raw);
        return raw;
      }

      @Override
      public String getVideoContentKind() {
        return item.getVideoContentKind();
      }

      @Override
      public void setVideoRawText(String videoRawText) {
        item.setVideoRawText(videoRawText);
      }

      @Override
      public void setVideoContentKind(String videoContentKind) {
        item.setVideoContentKind(videoContentKind);
      }

      @Override
      public void setVideoParseStatus(String videoParseStatus) {
        item.setVideoParseStatus(videoParseStatus);
      }

      @Override
      public void setVideoParseStep(String videoParseStep) {
        item.setVideoParseStep(videoParseStep);
      }

      @Override
      public void setVideoParseError(String videoParseError) {
        item.setVideoParseError(videoParseError);
      }

      @Override
      public void setVideoParsedAt(LocalDateTime videoParsedAt) {
        item.setVideoParsedAt(videoParsedAt);
      }

      @Override
      public void setSummary(String summary) {
        item.setSummary(summary);
      }

      @Override
      public void setVideoDurationSeconds(Integer videoDurationSeconds) {
        // 信源表无时长列；资料池展示不依赖此处
      }

      @Override
      public void persist() {
        // 所属表写摘要等；解析状态/原文/类型 sole-write 共享表
        sourceItemMapper.updateById(item);
        videoParseRuns.upsertFromSourceItem(item);
      }
    };
  }

  /**
   * 标记解析步骤为运行中并落库。
   *
   * @param item 落库目标
   * @param step 当前步骤
   */
  public void markRunning(VideoParseSink item, VideoParseStep step) {
    item.setVideoParseStatus(VideoParseStatus.RUNNING.getValue());
    item.setVideoParseStep(step.getValue());
    item.setVideoParseError(null);
    item.persist();
  }

  /**
   * 标记解析失败并落库。
   *
   * @param item  落库目标
   * @param step  失败步骤
   * @param error 原始错误信息
   */
  public void fail(VideoParseSink item, VideoParseStep step, String error) {
    item.setVideoParseStatus(VideoParseStatus.FAILED.getValue());
    item.setVideoParseStep(step == null ? null : step.getValue());
    String human = VideoParseErrorMessages.humanize(error, step);
    if (StrUtil.isNotBlank(error) && !error.equals(human)) {
      log.warn("视频解析失败 itemId={} step={} raw={}", item.getId(),
          step == null ? null : step.getValue(), StrUtil.maxLength(error, 500));
    }
    item.setVideoParseError(StrUtil.maxLength(human, 500));
    item.persist();
  }
}
