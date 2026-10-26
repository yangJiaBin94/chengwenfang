package com.chengwenfang.notification.application;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.enums.JobStatus;
import com.chengwenfang.notification.domain.entity.NotificationEntity;
import com.chengwenfang.notification.facade.NotificationFacade;
import com.chengwenfang.notification.facade.constant.NotificationMetadataKeys;
import com.chengwenfang.notification.facade.constant.NotificationTypes;
import com.chengwenfang.notification.facade.enums.NotificationLevel;
import com.chengwenfang.notification.facade.enums.NotificationStatus;
import com.chengwenfang.notification.facade.dto.view.NotificationListView;
import com.chengwenfang.notification.facade.dto.view.NotificationSummaryView;
import com.chengwenfang.notification.facade.dto.view.NotificationView;
import com.chengwenfang.notification.infrastructure.mapper.NotificationMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link NotificationFacade} 实现：通知读写、去重创建与终态订阅。
 * <p>任务终态通过 {@link #createForTerminalJob} 按 dedupeKey 幂等落库，避免重复提醒。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFacadeImpl implements NotificationFacade {
  /** 通知表 Mapper。 */
  private final NotificationMapper notificationMapper;
  /** 实体 → 对外视图映射。 */
  private final NotificationViewMapper viewMapper;

  /**
   * 列出未读通知（最多 100 条，按创建时间倒序）。
   *
   * @return 未读通知视图列表
   */
  @Override
  public List<NotificationView> listUnread() {
    // 仅拉 unread，限制条数避免一次加载过多
    return notificationMapper.selectList(new LambdaQueryWrapper<NotificationEntity>()
            .eq(NotificationEntity::getStatus, NotificationStatus.UNREAD.getValue())
            .orderByDesc(NotificationEntity::getCreatedAt)
            .last("LIMIT 100"))
        .stream()
        .map(viewMapper::toView)
        .toList();
  }

  /**
   * 按状态分页/限量列出通知，并附带当前未读数。
   *
   * @param status 过滤状态；空或 {@code all} 表示不过滤；其它值按 status 等值匹配
   * @param limit  返回条数上限；null/≤0 默认 30，硬顶 200
   * @return 含 items、unreadCount 的列表视图（nextCursor 暂未实现）
   */
  @Override
  public NotificationListView list(String status, Integer limit) {
    // 统一钳位 limit，防止误传超大值
    int capped = Math.max(1, Math.min(limit == null || limit <= 0 ? 30 : limit, 200));
    LambdaQueryWrapper<NotificationEntity> query = new LambdaQueryWrapper<NotificationEntity>()
        .orderByDesc(NotificationEntity::getCreatedAt)
        .last("LIMIT " + capped);
    // status=all 或空：不按状态过滤
    if (StrUtil.isNotBlank(status) && !"all".equalsIgnoreCase(status)) {
      query.eq(NotificationEntity::getStatus, status);
    }
    List<NotificationView> items = notificationMapper.selectList(query).stream().map(viewMapper::toView).toList();
    return NotificationListView.builder()
        .items(items)
        .nextCursor(null)
        .unreadCount(unreadCount())
        .build();
  }

  /**
   * 通知汇总：未读数与最新一条创建时间。
   *
   * @return 汇总视图；无记录时 latestCreatedAt 为 null
   */
  @Override
  public NotificationSummaryView summary() {
    // 取最新一条仅用于展示时间戳
    NotificationEntity latest = notificationMapper.selectOne(new LambdaQueryWrapper<NotificationEntity>()
        .orderByDesc(NotificationEntity::getCreatedAt)
        .last("LIMIT 1"));
    return NotificationSummaryView.builder()
        .unreadCount(unreadCount())
        .latestCreatedAt(latest == null || latest.getCreatedAt() == null
            ? null
            : latest.getCreatedAt().toString())
        .build();
  }

  /**
   * 将指定通知标为已读（便捷重载）。
   *
   * @param id 通知主键
   * @return 更新后的通知视图
   * @throws BusinessException 通知不存在时 404
   */
  @Override
  public NotificationView markRead(String id) {
    // 默认标已读
    return markRead(id, true);
  }

  /**
   * 设置通知已读/未读。
   *
   * @param id   通知主键
   * @param read true→read 并写 readAt；false→unread 并清空 readAt
   * @return 更新后的通知视图
   * @throws BusinessException 通知不存在时 404
   */
  @Override
  @Transactional
  public NotificationView markRead(String id, boolean read) {
    NotificationEntity entity = notificationMapper.selectById(id);
    if (entity == null) {
      throw new BusinessException("notification_not_found", "通知不存在", 404);
    }
    LocalDateTime now = LocalDateTime.now();
    entity.setStatus(read
        ? NotificationStatus.READ.getValue()
        : NotificationStatus.UNREAD.getValue());
    // 未读时清空已读时间，便于前端区分
    entity.setReadAt(read ? now : null);
    notificationMapper.updateById(entity);
    return viewMapper.toView(entity);
  }

  /**
   * 将全部未读通知标为已读，并返回最新汇总。
   *
   * @return 标读后的汇总视图
   */
  @Override
  @Transactional
  public NotificationSummaryView readAll() {
    List<NotificationEntity> unread = notificationMapper.selectList(new LambdaQueryWrapper<NotificationEntity>()
        .eq(NotificationEntity::getStatus, NotificationStatus.UNREAD.getValue()));
    LocalDateTime now = LocalDateTime.now();
    // 逐条更新；体量通常不大
    for (NotificationEntity entity : unread) {
      entity.setStatus(NotificationStatus.READ.getValue());
      entity.setReadAt(now);
      notificationMapper.updateById(entity);
    }
    return summary();
  }

  /**
   * 任务终态时创建去重通知：同 jobType+jobId+status 仅保留一条。
   *
   * @param jobType  任务类型（如 hot_news_article_generation），影响通知 type 文案
   * @param jobId    任务 ID，参与去重键
   * @param entityId 关联业务实体 ID（课程/文章等），写入对应结构化列
   * @param status   终态状态字符串；与 {@link JobStatus#SUCCEEDED} 比较决定成败文案
   * @param title    通知标题；空则用类型默认标题
   * @param message  通知正文；null 转空串
   * @param metadata 结构化字段来源（仅盘点键落列）
   * @return 新建或已存在的通知视图
   */
  @Override
  @Transactional
  public NotificationView createForTerminalJob(
      String jobType,
      String jobId,
      String entityId,
      String status,
      String title,
      String message,
      Map<String, Object> metadata) {
    // 同 job+status 去重，避免终态事件重复投递
    String dedupeKey = StrUtil.nullToEmpty(jobType) + ":" + StrUtil.nullToEmpty(jobId) + ":" + StrUtil.nullToEmpty(status);
    NotificationEntity existing = notificationMapper.selectOne(new LambdaQueryWrapper<NotificationEntity>()
        .eq(NotificationEntity::getDedupeKey, dedupeKey)
        .last("LIMIT 1"));
    if (existing != null) {
      return viewMapper.toView(existing);
    }

    boolean succeeded = JobStatus.SUCCEEDED.getValue().equalsIgnoreCase(status);
    // 按任务类型映射业务通知 type
    String type = resolveType(jobType, succeeded);
    Map<String, Object> meta = ObjectUtil.defaultIfNull(metadata, Map.of());
    LocalDateTime now = LocalDateTime.now();
    NotificationEntity entity = NotificationEntity.builder()
        .id(IdsUtil.newId())
        .notificationType(type)
        .level(succeeded
            ? NotificationLevel.SUCCESS.getValue()
            : NotificationLevel.ERROR.getValue())
        .title(StrUtil.blankToDefault(title, defaultTitle(type)))
        .message(StrUtil.nullToEmpty(message))
        .status(NotificationStatus.UNREAD.getValue())
        .readAt(null)
        .dedupeKey(dedupeKey)
        .jobId(jobId)
        .jobType(StrUtil.blankToDefault(stringMeta(meta, NotificationMetadataKeys.JOB_TYPE, null), jobType))
        .workflowRunId(null)
        .modeCode(blankToNull(stringMeta(meta, NotificationMetadataKeys.MODE, null)))
        .themeId(blankToNull(stringMeta(meta, NotificationMetadataKeys.THEME_ID, null)))
        .themeCode(blankToNull(stringMeta(meta, NotificationMetadataKeys.THEME_CODE, null)))
        .themeName(blankToNull(stringMeta(meta, NotificationMetadataKeys.THEME_NAME, null)))
        .workId(resolveWorkId(jobType, entityId, meta))
        .outputId(blankToNull(stringMeta(meta, NotificationMetadataKeys.DOCUMENT_ID, null)))
        .draftOutputId(blankToNull(stringMeta(meta, NotificationMetadataKeys.DRAFT_DOCUMENT_ID, null)))
        .topicId(blankToNull(stringMeta(meta, NotificationMetadataKeys.TOPIC_ID, null)))
        .fetchRunId(blankToNull(stringMeta(meta, NotificationMetadataKeys.FETCH_RUN_ID, null)))
        .researchRunId(blankToNull(stringMeta(meta, NotificationMetadataKeys.RESEARCH_RUN_ID, null)))
        .subjectTitle(stringMeta(meta, NotificationMetadataKeys.COURSE_TITLE, StrUtil.nullToEmpty(entityId)))
        .articleTitle(blankToNull(stringMeta(meta, NotificationMetadataKeys.ARTICLE_TITLE, null)))
        .seriesName(stringMeta(meta, NotificationMetadataKeys.SERIES_NAME, ""))
        .lessonNo(intMeta(meta, NotificationMetadataKeys.LESSON_NO, 0))
        .errorCode(blankToNull(stringMeta(meta, NotificationMetadataKeys.ERROR_CODE, null)))
        .rawErrorMessage(blankToNull(stringMeta(meta, NotificationMetadataKeys.RAW_ERROR_MESSAGE, null)))
        .createdAt(now)
        .build();
    // 抓取/调研等：entityId 落对应列（enrich 已写时不覆盖）
    applyEntityIdFallback(entity, jobType, entityId);
    notificationMapper.insert(entity);
    log.info("创建通知 id={} type={} jobId={}", entity.getId(), type, jobId);
    return viewMapper.toView(entity);
  }

  /**
   * 解析作品 ID：热点文章/课程类任务写入 work_id。
   *
   * @param jobType  任务类型
   * @param entityId 业务实体 ID
   * @param meta     metadata
   * @return work_id；非作品任务返回 null
   */
  private static String resolveWorkId(String jobType, String entityId, Map<String, Object> meta) {
    String fromMeta = blankToNull(stringMeta(meta, NotificationMetadataKeys.ARTICLE_ID, null));
    if (fromMeta != null) {
      return fromMeta;
    }
    if (isWorkEntityJob(jobType) && StrUtil.isNotBlank(entityId)) {
      return entityId.trim();
    }
    return null;
  }

  /**
   * 非作品任务：把 entityId 回落到抓取/调研/选题/主题列。
   *
   * @param entity   待写入实体
   * @param jobType  任务类型
   * @param entityId 业务实体 ID
   */
  private static void applyEntityIdFallback(NotificationEntity entity, String jobType, String entityId) {
    if (StrUtil.isBlank(entityId)) {
      return;
    }
    String trimmed = entityId.trim();
    if (JobTypes.HOT_NEWS_FETCH.equals(jobType) && StrUtil.isBlank(entity.getFetchRunId())) {
      entity.setFetchRunId(trimmed);
      return;
    }
    if (JobTypes.HOT_NEWS_TOPIC_RESEARCH.equals(jobType) && StrUtil.isBlank(entity.getResearchRunId())) {
      entity.setResearchRunId(trimmed);
      return;
    }
    if (JobTypes.HOT_NEWS_VIDEO_PARSE.equals(jobType) && StrUtil.isBlank(entity.getTopicId())) {
      entity.setTopicId(trimmed);
      return;
    }
    if ((JobTypes.HOT_NEWS_SOURCE_VIDEO_PARSE.equals(jobType)
            || JobTypes.THEME_CURRICULUM_GENERATION.equals(jobType))
        && StrUtil.isBlank(entity.getThemeId())) {
      entity.setThemeId(trimmed);
    }
  }

  /**
   * 是否为作品实体任务（entityId 即 work_id）。
   *
   * @param jobType 任务类型
   * @return 是则 true
   */
  private static boolean isWorkEntityJob(String jobType) {
    return JobTypes.HOT_NEWS_ARTICLE_GENERATION.equals(jobType)
        || JobTypes.HOT_NEWS_ARTICLE_REWRITE.equals(jobType)
        || JobTypes.HOT_ARTICLE_REVIEW.equals(jobType)
        || JobTypes.HOT_ARTICLE_REVIEW_REPAIR.equals(jobType)
        || JobTypes.HOT_ARTICLE_REVIEW_REVALIDATE.equals(jobType)
        || JobTypes.COURSE_CONTENT_GENERATION.equals(jobType)
        || JobTypes.COURSE_DRAFT_GENERATION.equals(jobType)
        || JobTypes.WECHAT_ARTICLE_GENERATION.equals(jobType)
        || JobTypes.IMAGE_GENERATION.equals(jobType)
        || JobTypes.ARTICLE_REPAIR.equals(jobType);
  }

  /**
   * 空白转 null。
   *
   * @param value 字符串
   * @return 非空白原值，否则 null
   */
  private static String blankToNull(String value) {
    return StrUtil.isBlank(value) ? null : value;
  }

  private static String stringMeta(Map<String, Object> meta, String key, String fallback) {
    Object value = meta.get(key);
    if (value == null) {
      return fallback;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? fallback : text;
  }

  private static int intMeta(Map<String, Object> meta, String key, int fallback) {
    Object value = meta.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value != null) {
      try {
        return Integer.parseInt(String.valueOf(value).trim());
      } catch (Exception ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  /**
   * 统计未读通知条数。
   *
   * @return 未读数量
   */
  private long unreadCount() {
    return notificationMapper.selectCount(new LambdaQueryWrapper<NotificationEntity>()
        .eq(NotificationEntity::getStatus, NotificationStatus.UNREAD.getValue()));
  }

  /**
   * 将 jobType + 成败映射为通知 type 码。
   *
   * @param jobType   任务类型字符串
   * @param succeeded 是否成功终态
   * @return 通知 type（如 course_generation_succeeded）
   */
  private static String resolveType(String jobType, boolean succeeded) {
    if (JobTypes.HOT_NEWS_ARTICLE_GENERATION.equals(jobType)) {
      return succeeded
          ? NotificationTypes.HOT_NEWS_GENERATION_SUCCEEDED
          : NotificationTypes.HOT_NEWS_GENERATION_FAILED;
    }
    if (JobTypes.COURSE_CONTENT_GENERATION.equals(jobType)) {
      return succeeded
          ? NotificationTypes.COURSE_GENERATION_SUCCEEDED
          : NotificationTypes.COURSE_GENERATION_FAILED;
    }
    // 其余任务统一用通用终态 type，具体业务靠 metadata.jobType
    return succeeded
        ? NotificationTypes.JOB_TERMINAL_SUCCEEDED
        : NotificationTypes.JOB_TERMINAL_FAILED;
  }

  /**
   * 按通知 type 给出默认中文标题。
   *
   * @param type 通知 type 码
   * @return 默认标题文案
   */
  private static String defaultTitle(String type) {
    if (NotificationTypes.COURSE_GENERATION_SUCCEEDED.equals(type)) {
      return "课程内容生成完成";
    }
    if (NotificationTypes.COURSE_GENERATION_FAILED.equals(type)) {
      return "课程内容生成失败";
    }
    if (NotificationTypes.HOT_NEWS_GENERATION_SUCCEEDED.equals(type)) {
      return "热点文章生成完成";
    }
    if (NotificationTypes.HOT_NEWS_GENERATION_FAILED.equals(type)) {
      return "热点文章生成失败";
    }
    if (NotificationTypes.JOB_TERMINAL_SUCCEEDED.equals(type)) {
      return "任务已完成";
    }
    if (NotificationTypes.JOB_TERMINAL_FAILED.equals(type)) {
      return "任务失败";
    }
    return "任务通知";
  }
}
