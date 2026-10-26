package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.sourcing.domain.entity.VideoTranscriptEntity;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import com.chengwenfang.sourcing.infrastructure.mapper.VideoTranscriptMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频解析结果读写：状态与原文文件均 sole-write {@code video_transcripts}。
 */
@Service
public class VideoParseRunService {
  /** 结果表 Mapper。 */
  private final VideoTranscriptMapper transcriptMapper;
  /** 原文文件读写。 */
  private final FileFacade files;

  /**
   * @param transcriptMapper 结果表 Mapper
   * @param files            文件门面
   */
  public VideoParseRunService(VideoTranscriptMapper transcriptMapper, FileFacade files) {
    this.transcriptMapper = transcriptMapper;
    this.files = files;
  }

  /**
   * 读取口播原文；无行或无文件则空串。
   *
   * @param ownerType 所属类型
   * @param ownerId   所属 id
   * @return 原文；无则空串
   */
  public String findRawText(String ownerType, String ownerId) {
    VideoTranscriptEntity row = find(ownerType, ownerId);
    if (row == null || StrUtil.isBlank(row.getTextFileId())) {
      return "";
    }
    return StrUtil.nullToEmpty(files.readText(row.getTextFileId()));
  }

  /**
   * 将共享行水合到资料池实体内存字段（解析状态 / 原文）。
   *
   * @param item 资料池实体
   */
  public void applyToResearchItem(TopicResearchItemEntity item) {
    if (item == null || StrUtil.isBlank(item.getId())) {
      return;
    }
    applyResearchRun(item, find(VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM, item.getId()));
  }

  /**
   * 批量水合资料池解析状态。
   *
   * @param items 资料池实体列表
   */
  public void applyToResearchItems(Collection<TopicResearchItemEntity> items) {
    if (CollUtil.isEmpty(items)) {
      return;
    }
    List<String> ids = items.stream()
        .filter(row -> row != null && StrUtil.isNotBlank(row.getId()))
        .map(TopicResearchItemEntity::getId)
        .distinct()
        .toList();
    Map<String, VideoTranscriptEntity> byOwner =
        mapByOwnerIds(VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM, ids);
    for (TopicResearchItemEntity item : items) {
      if (item == null || StrUtil.isBlank(item.getId())) {
        continue;
      }
      applyResearchRun(item, byOwner.get(item.getId()));
    }
  }

  /**
   * 将共享行水合到信源实体内存字段。
   *
   * @param item 信源实体
   */
  public void applyToSourceItem(SourceItemEntity item) {
    if (item == null || StrUtil.isBlank(item.getId())) {
      return;
    }
    applySourceRun(item, find(VideoParseOwnerTypes.SOURCE_ITEM, item.getId()));
  }

  /**
   * 批量水合信源解析状态。
   *
   * @param items 信源实体列表
   */
  public void applyToSourceItems(Collection<SourceItemEntity> items) {
    if (CollUtil.isEmpty(items)) {
      return;
    }
    List<String> ids = items.stream()
        .filter(row -> row != null && StrUtil.isNotBlank(row.getId()))
        .map(SourceItemEntity::getId)
        .distinct()
        .toList();
    Map<String, VideoTranscriptEntity> byOwner =
        mapByOwnerIds(VideoParseOwnerTypes.SOURCE_ITEM, ids);
    for (SourceItemEntity item : items) {
      if (item == null || StrUtil.isBlank(item.getId())) {
        continue;
      }
      applySourceRun(item, byOwner.get(item.getId()));
    }
  }

  /**
   * 视频条目首次入库：若尚无共享行则写入 {@code none}。
   *
   * @param ownerType 所属类型
   * @param ownerId   所属 id
   * @param url       视频 URL
   */
  @Transactional
  public void ensureNone(String ownerType, String ownerId, String url) {
    ensureNone(ownerType, ownerId, url, null, null);
  }

  /**
   * 视频条目首次入库：若尚无共享行则写入 {@code none}，并可带时长/内容类型。
   *
   * @param ownerType       所属类型
   * @param ownerId         所属 id
   * @param url             视频 URL
   * @param contentKind     speech/visual；可空
   * @param durationSeconds 时长秒；可空
   */
  @Transactional
  public void ensureNone(
      String ownerType,
      String ownerId,
      String url,
      String contentKind,
      Integer durationSeconds) {
    if (StrUtil.isBlank(ownerType) || StrUtil.isBlank(ownerId)) {
      return;
    }
    VideoTranscriptEntity existing = find(ownerType, ownerId);
    if (existing != null) {
      boolean dirty = false;
      if (existing.getDurationSeconds() == null && durationSeconds != null) {
        existing.setDurationSeconds(durationSeconds);
        dirty = true;
      }
      if (StrUtil.isBlank(existing.getContentKind()) && StrUtil.isNotBlank(contentKind)) {
        existing.setContentKind(contentKind);
        dirty = true;
      }
      if (dirty) {
        existing.setUpdatedAt(LocalDateTime.now());
        transcriptMapper.updateById(existing);
      }
      return;
    }
    upsert(
        ownerType,
        ownerId,
        url,
        VideoParseStatus.NONE.getValue(),
        null,
        null,
        null,
        contentKind,
        durationSeconds,
        null);
  }

  /**
   * 按资料池条目 upsert 共享行（状态 sole-write 本表）。
   *
   * @param item 资料池实体
   */
  @Transactional
  public void upsertFromResearchItem(TopicResearchItemEntity item) {
    if (item == null || StrUtil.isBlank(item.getId())) {
      return;
    }
    upsert(
        VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM,
        item.getId(),
        item.getUrl(),
        item.getVideoParseStatus(),
        item.getVideoParseStep(),
        item.getVideoParseError(),
        item.getVideoRawText(),
        item.getVideoContentKind(),
        item.getVideoDurationSeconds(),
        item.getVideoParsedAt());
  }

  /**
   * 按信源条目 upsert 共享行。
   *
   * @param item 信源实体
   */
  @Transactional
  public void upsertFromSourceItem(SourceItemEntity item) {
    if (item == null || StrUtil.isBlank(item.getId())) {
      return;
    }
    upsert(
        VideoParseOwnerTypes.SOURCE_ITEM,
        item.getId(),
        item.getUrl(),
        item.getVideoParseStatus(),
        item.getVideoParseStep(),
        item.getVideoParseError(),
        item.getVideoRawText(),
        item.getVideoContentKind(),
        null,
        item.getVideoParsedAt());
  }

  /**
   * 写入/更新共享行；原文落文件。
   *
   * @param ownerType       所属类型
   * @param ownerId         所属 id
   * @param url             URL
   * @param status          状态
   * @param step            步骤
   * @param error           错误
   * @param rawText         原文
   * @param contentKind     内容类型
   * @param durationSeconds 时长
   * @param parsedAt        成功时间
   */
  @Transactional
  public void upsert(
      String ownerType,
      String ownerId,
      String url,
      String status,
      String step,
      String error,
      String rawText,
      String contentKind,
      Integer durationSeconds,
      LocalDateTime parsedAt) {
    LocalDateTime now = LocalDateTime.now();
    VideoTranscriptEntity existing = find(ownerType, ownerId);
    if (existing == null) {
      String id = IdsUtil.newId();
      VideoTranscriptEntity created = VideoTranscriptEntity.builder()
          .id(id)
          .url(url)
          .status(status)
          .step(step)
          .errorText(error)
          .contentKind(contentKind)
          .durationSeconds(durationSeconds)
          .parsedAt(parsedAt)
          .createdAt(now)
          .updatedAt(now)
          .build();
      applyOwner(created, ownerType, ownerId);
      created.setTextFileId(writeRawText(id, rawText));
      transcriptMapper.insert(created);
      return;
    }
    existing.setUrl(url);
    existing.setStatus(status);
    existing.setStep(step);
    existing.setErrorText(error);
    existing.setContentKind(contentKind);
    existing.setDurationSeconds(durationSeconds);
    existing.setParsedAt(parsedAt);
    existing.setUpdatedAt(now);
    existing.setTextFileId(replaceRawText(existing.getId(), existing.getTextFileId(), rawText));
    transcriptMapper.updateById(existing);
  }

  /**
   * 按 owner 查共享行。
   *
   * @param ownerType 类型
   * @param ownerId   id
   * @return 行；无则 null
   */
  public VideoTranscriptEntity find(String ownerType, String ownerId) {
    if (StrUtil.isBlank(ownerType) || StrUtil.isBlank(ownerId)) {
      return null;
    }
    LambdaQueryWrapper<VideoTranscriptEntity> query = new LambdaQueryWrapper<>();
    if (VideoParseOwnerTypes.SOURCE_ITEM.equals(ownerType)) {
      query.eq(VideoTranscriptEntity::getSourceItemId, ownerId);
    } else if (VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM.equals(ownerType)) {
      query.eq(VideoTranscriptEntity::getResearchItemId, ownerId);
    } else {
      return null;
    }
    return transcriptMapper.selectOne(query.last("LIMIT 1"));
  }

  /**
   * 按 owner_id 批量加载共享行。
   *
   * @param ownerType 类型
   * @param ownerIds  id 列表
   * @return ownerId → 行
   */
  public Map<String, VideoTranscriptEntity> mapByOwnerIds(String ownerType, List<String> ownerIds) {
    Map<String, VideoTranscriptEntity> out = new HashMap<>();
    if (StrUtil.isBlank(ownerType) || CollUtil.isEmpty(ownerIds)) {
      return out;
    }
    LambdaQueryWrapper<VideoTranscriptEntity> query = new LambdaQueryWrapper<>();
    Function<VideoTranscriptEntity, String> ownerKey;
    if (VideoParseOwnerTypes.SOURCE_ITEM.equals(ownerType)) {
      query.in(VideoTranscriptEntity::getSourceItemId, ownerIds);
      ownerKey = VideoTranscriptEntity::getSourceItemId;
    } else if (VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM.equals(ownerType)) {
      query.in(VideoTranscriptEntity::getResearchItemId, ownerIds);
      ownerKey = VideoTranscriptEntity::getResearchItemId;
    } else {
      return out;
    }
    List<VideoTranscriptEntity> rows = transcriptMapper.selectList(query);
    for (VideoTranscriptEntity row : rows) {
      if (row == null) {
        continue;
      }
      String key = ownerKey.apply(row);
      if (StrUtil.isNotBlank(key)) {
        out.put(key, row);
      }
    }
    return out;
  }

  /**
   * 设置 owner 外键（二选一）。
   *
   * @param row       行
   * @param ownerType 类型
   * @param ownerId   id
   */
  private static void applyOwner(VideoTranscriptEntity row, String ownerType, String ownerId) {
    if (VideoParseOwnerTypes.SOURCE_ITEM.equals(ownerType)) {
      row.setSourceItemId(ownerId);
      row.setResearchItemId(null);
    } else if (VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM.equals(ownerType)) {
      row.setResearchItemId(ownerId);
      row.setSourceItemId(null);
    }
  }

  /**
   * 写入原文文件；空白则不建文件。
   *
   * @param transcriptId 行 id
   * @param rawText      原文
   * @return 文件 id；无正文则 null
   */
  private String writeRawText(String transcriptId, String rawText) {
    if (rawText == null) {
      return null;
    }
    String objectKey = "video-transcripts/" + transcriptId + ".txt";
    StoredFileView stored = files.writeText(objectKey, transcriptId + ".txt", rawText);
    return stored == null ? null : stored.getId();
  }

  /**
   * 覆盖原文文件；null 表示清空文件引用。
   *
   * @param transcriptId 行 id
   * @param previousId   原文件 id
   * @param rawText      新原文；null 清空
   * @return 新文件 id
   */
  private String replaceRawText(String transcriptId, String previousId, String rawText) {
    if (rawText == null) {
      return previousId;
    }
    return writeRawText(transcriptId, rawText);
  }

  /**
   * 共享行字段拷贝到资料池内存字段。
   *
   * @param item 资料池实体
   * @param run  共享行；null 时不改
   */
  private void applyResearchRun(TopicResearchItemEntity item, VideoTranscriptEntity run) {
    if (run == null) {
      return;
    }
    item.setVideoParseStatus(run.getStatus());
    item.setVideoParseStep(run.getStep());
    item.setVideoParseError(run.getErrorText());
    item.setVideoParsedAt(run.getParsedAt());
    if (StrUtil.isBlank(item.getVideoRawText()) && StrUtil.isNotBlank(run.getTextFileId())) {
      item.setVideoRawText(files.readText(run.getTextFileId()));
    }
    if (StrUtil.isBlank(item.getVideoContentKind()) && StrUtil.isNotBlank(run.getContentKind())) {
      item.setVideoContentKind(run.getContentKind());
    }
    if (item.getVideoDurationSeconds() == null && run.getDurationSeconds() != null) {
      item.setVideoDurationSeconds(run.getDurationSeconds());
    }
  }

  /**
   * 共享行字段拷贝到信源内存字段。
   *
   * @param item 信源实体
   * @param run  共享行；null 时不改
   */
  private void applySourceRun(SourceItemEntity item, VideoTranscriptEntity run) {
    if (run == null) {
      return;
    }
    item.setVideoParseStatus(run.getStatus());
    item.setVideoParseStep(run.getStep());
    item.setVideoParseError(run.getErrorText());
    item.setVideoParsedAt(run.getParsedAt());
    if (StrUtil.isBlank(item.getVideoRawText()) && StrUtil.isNotBlank(run.getTextFileId())) {
      item.setVideoRawText(files.readText(run.getTextFileId()));
    }
    if (StrUtil.isBlank(item.getVideoContentKind()) && StrUtil.isNotBlank(run.getContentKind())) {
      item.setVideoContentKind(run.getContentKind());
    }
  }
}
