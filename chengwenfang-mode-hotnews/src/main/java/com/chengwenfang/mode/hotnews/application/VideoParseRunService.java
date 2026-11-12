package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.FileView;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import com.chengwenfang.mode.hotnews.application.generation.hotnews.model.HotNewsVideoFrameEvidence;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsEvidenceKeys;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.sourcing.domain.entity.VideoTranscriptEntity;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import com.chengwenfang.sourcing.infrastructure.mapper.VideoTranscriptMapper;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频解析结果读写：状态与原文文件均 sole-write {@code video_transcripts}。
 */
@Slf4j
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
   * 资料池整池替换时：将解析行改绑到新 researchItemId，保留原文与状态。
   *
   * @param transcript        已有共享行
   * @param newResearchItemId 新资料条目 id
   * @param url               视频 URL；可空则不改
   * @param durationSeconds   时长；仅当行上为空时写入
   */
  @Transactional
  public void rebindResearchItem(
      VideoTranscriptEntity transcript,
      String newResearchItemId,
      String url,
      Integer durationSeconds) {
    if (transcript == null || StrUtil.isBlank(newResearchItemId)) {
      return;
    }
    transcript.setResearchItemId(newResearchItemId);
    transcript.setSourceItemId(null);
    if (StrUtil.isNotBlank(url)) {
      transcript.setUrl(url);
    }
    if (transcript.getDurationSeconds() == null && durationSeconds != null) {
      transcript.setDurationSeconds(durationSeconds);
    }
    transcript.setUpdatedAt(LocalDateTime.now());
    transcriptMapper.updateById(transcript);
  }

  /**
   * 删除资料池解析共享行及原文/抽帧文件（整池替换后未再绑定的孤儿）。
   *
   * @param transcriptIds 行主键集合
   */
  @Transactional
  public void deleteByIds(Collection<String> transcriptIds) {
    if (CollUtil.isEmpty(transcriptIds)) {
      return;
    }
    List<VideoTranscriptEntity> rows = transcriptMapper.selectList(
        new LambdaQueryWrapper<VideoTranscriptEntity>()
            .in(VideoTranscriptEntity::getId, transcriptIds));
    for (VideoTranscriptEntity row : rows) {
      if (row == null) {
        continue;
      }
      deleteStoredQuietly(row.getTextFileId());
      for (String frameId : parseFrameFileIds(row.getFrameFileIds())) {
        deleteStoredQuietly(frameId);
      }
    }
    transcriptMapper.delete(new LambdaQueryWrapper<VideoTranscriptEntity>()
        .in(VideoTranscriptEntity::getId, transcriptIds));
  }

  /**
   * 将工作目录 frames 落盘到 stored_files，并写回 {@code frame_file_ids}/{@code frame_captions}。
   * 已有抽帧时跳过；无帧文件时不改行。
   *
   * @param itemId   资料池或信源条目 id
   * @param workDir  解析工作目录
   * @param captions 与帧文件对齐的描述；可空或短于帧数
   * @return 写入后的文件 id 列表；未写入则为空
   */
  @Transactional
  public List<String> persistFramesFromWorkDir(String itemId, Path workDir, List<String> captions) {
    if (StrUtil.isBlank(itemId) || workDir == null) {
      return List.of();
    }
    VideoTranscriptEntity row = findRowByItemId(itemId);
    if (row == null) {
      log.warn("视频抽帧落盘跳过：无解析行 itemId={}", itemId);
      return List.of();
    }
    List<String> existing = parseFrameFileIds(row.getFrameFileIds());
    if (CollUtil.isNotEmpty(existing)) {
      // 已有抽帧但尚无可用描述时补写（空串数组不算可用）
      if (!hasUsableCaptions(row.getFrameCaptions()) && hasUsableCaptionsList(captions)) {
        row.setFrameCaptions(JSONUtil.toJsonStr(alignCaptions(existing.size(), captions)));
        row.setUpdatedAt(LocalDateTime.now());
        transcriptMapper.updateById(row);
      }
      return existing;
    }
    Path framesDir = workDir.resolve("frames");
    if (!Files.isDirectory(framesDir)) {
      return List.of();
    }
    List<Path> frameFiles = listFrameImageFiles(framesDir);
    if (frameFiles.isEmpty()) {
      return List.of();
    }
    int limit = Math.min(frameFiles.size(), HotNewsEvidenceKeys.MAX_PERSISTED_VIDEO_FRAMES);
    List<String> captionByFrame = alignCaptions(limit, captions);
    List<String> savedIds = new ArrayList<>();
    List<String> savedCaptions = new ArrayList<>();
    for (int index = 0; index < limit; index++) {
      Path framePath = frameFiles.get(index);
      try {
        byte[] bytes = Files.readAllBytes(framePath);
        String fileName = framePath.getFileName().toString();
        String objectKey = HotNewsEvidenceKeys.VIDEO_FRAME_OBJECT_KEY_PREFIX
            + row.getId() + "/" + fileName;
        String contentType = fileName.toLowerCase(Locale.ROOT).endsWith(".png")
            ? "image/png"
            : "image/jpeg";
        FileView saved = files.saveLocalBytes(objectKey, fileName, contentType, bytes);
        if (saved != null && StrUtil.isNotBlank(saved.getId())) {
          // 按实际落盘成功的帧配对描述，避免中间失败导致错位
          savedIds.add(saved.getId());
          savedCaptions.add(captionByFrame.get(index));
        }
      } catch (Exception exception) {
        log.warn(
            "视频抽帧落盘失败 itemId={} file={} err={}",
            itemId,
            framePath.getFileName(),
            exception.getMessage());
      }
    }
    if (savedIds.isEmpty()) {
      return List.of();
    }
    row.setFrameFileIds(JSONUtil.toJsonStr(savedIds));
    // 全空描述不写列，便于下次补写
    row.setFrameCaptions(hasUsableCaptionsList(savedCaptions)
        ? JSONUtil.toJsonStr(savedCaptions)
        : null);
    row.setUpdatedAt(LocalDateTime.now());
    transcriptMapper.updateById(row);
    log.info(
        "视频抽帧已保留 itemId={} transcriptId={} frames={} captionsUsable={}",
        itemId,
        row.getId(),
        savedIds.size(),
        hasUsableCaptionsList(savedCaptions));
    return savedIds;
  }

  /**
   * 是否已有保留抽帧。
   *
   * @param itemId 条目 id
   * @return 有抽帧为 true
   */
  public boolean hasPersistedFrames(String itemId) {
    VideoTranscriptEntity row = findRowByItemId(itemId);
    return row != null && CollUtil.isNotEmpty(parseFrameFileIds(row.getFrameFileIds()));
  }

  /**
   * 是否已有可用的逐帧描述（至少一个非空）。
   *
   * @param itemId 条目 id
   * @return 有可用描述为 true
   */
  public boolean hasUsableFrameCaptions(String itemId) {
    VideoTranscriptEntity row = findRowByItemId(itemId);
    return row != null && hasUsableCaptions(row.getFrameCaptions());
  }

  /**
   * 按视频 URL 查找已保留抽帧（含描述，成文佐证用）。
   *
   * @param url 视频 URL
   * @return 抽帧列表；无则空
   */
  public List<HotNewsVideoFrameEvidence> findFrameEvidenceByUrl(String url) {
    Set<String> aliases = urlAliases(url);
    if (aliases.isEmpty()) {
      return List.of();
    }
    List<VideoTranscriptEntity> rows = transcriptMapper.selectList(
        new LambdaQueryWrapper<VideoTranscriptEntity>()
            .isNotNull(VideoTranscriptEntity::getFrameFileIds)
            .ne(VideoTranscriptEntity::getFrameFileIds, "")
            .ne(VideoTranscriptEntity::getFrameFileIds, "[]")
            .and(wrapper -> {
              boolean first = true;
              for (String alias : aliases) {
                if (first) {
                  wrapper.apply("LOWER(TRIM(TRAILING '/' FROM url)) = {0}", alias);
                  first = false;
                } else {
                  wrapper.or().apply("LOWER(TRIM(TRAILING '/' FROM url)) = {0}", alias);
                }
              }
            })
            .orderByDesc(VideoTranscriptEntity::getUpdatedAt)
            .last("LIMIT 5"));
    for (VideoTranscriptEntity row : rows) {
      if (row == null) {
        continue;
      }
      List<String> ids = parseFrameFileIds(row.getFrameFileIds());
      if (CollUtil.isEmpty(ids)) {
        continue;
      }
      List<String> captions = alignCaptions(ids.size(), parseFrameCaptions(row.getFrameCaptions()));
      List<HotNewsVideoFrameEvidence> frames = new ArrayList<>();
      for (int index = 0; index < ids.size(); index++) {
        frames.add(HotNewsVideoFrameEvidence.builder()
            .fileId(ids.get(index))
            .caption(captions.get(index))
            .build());
      }
      return frames;
    }
    return List.of();
  }

  /**
   * 按视频 URL 查找已保留抽帧文件 id。
   *
   * @param url 视频 URL
   * @return 文件 id 列表；无则空
   */
  public List<String> findFrameFileIdsByUrl(String url) {
    return findFrameEvidenceByUrl(url).stream()
        .map(HotNewsVideoFrameEvidence::getFileId)
        .filter(StrUtil::isNotBlank)
        .collect(Collectors.toList());
  }

  /**
   * 从 JSON 列解析抽帧文件 id。
   *
   * @param raw JSON 数组或空
   * @return id 列表
   */
  public static List<String> parseFrameFileIds(String raw) {
    if (StrUtil.isBlank(raw) || !JSONUtil.isTypeJSONArray(raw)) {
      return List.of();
    }
    List<String> ids = JSONUtil.toList(raw, String.class);
    if (CollUtil.isEmpty(ids)) {
      return List.of();
    }
    return ids.stream().filter(StrUtil::isNotBlank).distinct().collect(Collectors.toList());
  }

  /**
   * 解析逐帧描述 JSON 数组。
   *
   * @param raw JSON 数组或空
   * @return 描述列表
   */
  public static List<String> parseFrameCaptions(String raw) {
    if (StrUtil.isBlank(raw) || !JSONUtil.isTypeJSONArray(raw)) {
      return List.of();
    }
    List<String> captions = JSONUtil.toList(raw, String.class);
    if (CollUtil.isEmpty(captions)) {
      return List.of();
    }
    return captions.stream().map(StrUtil::nullToEmpty).collect(Collectors.toList());
  }

  /**
   * 将描述列表对齐到指定长度。
   *
   * @param size     目标长度
   * @param captions 原始描述
   * @return 对齐后的描述
   */
  public static List<String> alignCaptions(int size, List<String> captions) {
    List<String> aligned = new ArrayList<>(Math.max(0, size));
    for (int index = 0; index < size; index++) {
      if (CollUtil.isNotEmpty(captions) && index < captions.size()) {
        aligned.add(StrUtil.nullToEmpty(captions.get(index)));
      } else {
        aligned.add("");
      }
    }
    return aligned;
  }

  /**
   * JSON 列是否含至少一条非空描述。
   *
   * @param raw JSON 数组或空
   * @return 有可用描述为 true
   */
  public static boolean hasUsableCaptions(String raw) {
    return hasUsableCaptionsList(parseFrameCaptions(raw));
  }

  /**
   * 列表是否含至少一条非空描述。
   *
   * @param captions 描述列表
   * @return 有可用描述为 true
   */
  public static boolean hasUsableCaptionsList(List<String> captions) {
    if (CollUtil.isEmpty(captions)) {
      return false;
    }
    return captions.stream().anyMatch(StrUtil::isNotBlank);
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

  /**
   * 按资料池或信源条目 id 查共享行。
   *
   * @param itemId 条目 id
   * @return 行；无则 null
   */
  private VideoTranscriptEntity findRowByItemId(String itemId) {
    VideoTranscriptEntity research = find(VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM, itemId);
    if (research != null) {
      return research;
    }
    return find(VideoParseOwnerTypes.SOURCE_ITEM, itemId);
  }

  /**
   * 列出 frames 目录下的图片，按文件名排序。
   *
   * @param framesDir frames 目录
   * @return 图片路径
   */
  private static List<Path> listFrameImageFiles(Path framesDir) {
    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(framesDir)) {
      for (Path path : stream) {
        if (!Files.isRegularFile(path)) {
          continue;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
          files.add(path);
        }
      }
    } catch (Exception exception) {
      return List.of();
    }
    files.sort((left, right) -> left.getFileName().toString().compareTo(right.getFileName().toString()));
    return files;
  }

  /**
   * 静默删除 stored_files。
   *
   * @param fileId 文件 id
   */
  private void deleteStoredQuietly(String fileId) {
    if (StrUtil.isBlank(fileId)) {
      return;
    }
    try {
      files.delete(fileId);
    } catch (Exception ignored) {
      // 文件缺失不阻断
    }
  }

  /**
   * URL 归一化（小写、去尾斜杠）。
   *
   * @param url 原始 URL
   * @return 归一化串
   */
  static String normalizeUrl(String url) {
    String trimmed = StrUtil.trim(url);
    if (StrUtil.isBlank(trimmed)) {
      return "";
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    while (lower.endsWith("/")) {
      lower = lower.substring(0, lower.length() - 1);
    }
    return lower;
  }

  /**
   * 生成可匹配的 URL 别名（含 http/https 互换）。
   *
   * @param url 原始 URL
   * @return 别名集合
   */
  static Set<String> urlAliases(String url) {
    Set<String> aliases = new LinkedHashSet<>();
    String normalized = normalizeUrl(url);
    if (StrUtil.isBlank(normalized)) {
      return aliases;
    }
    aliases.add(normalized);
    if (normalized.startsWith("https://")) {
      aliases.add("http://" + normalized.substring("https://".length()));
    } else if (normalized.startsWith("http://")) {
      aliases.add("https://" + normalized.substring("http://".length()));
    }
    return aliases;
  }
}
