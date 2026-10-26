package com.workbench.notification.application;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.workbench.content.facade.constant.ContentModeIds;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.notification.domain.entity.NotificationEntity;
import com.workbench.notification.facade.constant.NotificationMetadataKeys;
import com.workbench.notification.facade.dto.view.NotificationView;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 通知实体与对外视图转换。 */
@Component
public class NotificationViewMapper {

  /**
   * 实体转对外视图；{@code metadata} 仅含前端/后端实际读取的键。
   *
   * @param entity 通知实体
   * @return 通知视图；入参为空时返回 null
   */
  public NotificationView toView(NotificationEntity entity) {
    if (entity == null) {
      return null;
    }
    return NotificationView.builder()
        .id(entity.getId())
        .type(entity.getNotificationType())
        .level(entity.getLevel())
        .title(entity.getTitle())
        .message(StrUtil.nullToEmpty(entity.getMessage()))
        .courseId(resolveCourseId(entity))
        .jobId(entity.getJobId())
        .workflowRunId(entity.getWorkflowRunId())
        .seriesName(StrUtil.nullToEmpty(entity.getSeriesName()))
        .courseTitle(StrUtil.nullToEmpty(entity.getSubjectTitle()))
        .lessonNo(ObjectUtil.defaultIfNull(entity.getLessonNo(), 0))
        .status(entity.getStatus())
        .metadata(assembleMetadata(entity))
        .createdAt(dateTimeToString(entity.getCreatedAt()))
        .readAt(dateTimeToString(entity.getReadAt()))
        .build();
  }

  /**
   * 还原顶层 courseId：优先作品 ID，否则回落抓取/调研/选题/主题（与原 entityId 写入 course_id 行为一致）。
   *
   * @param entity 通知实体
   * @return courseId；可能为空
   */
  private static String resolveCourseId(NotificationEntity entity) {
    return StrUtil.firstNonBlank(
        entity.getWorkId(),
        entity.getFetchRunId(),
        entity.getResearchRunId(),
        entity.getTopicId(),
        entity.getThemeId());
  }

  /**
   * 按盘点键组装 metadata（LinkedHashMap，仅非空列）。
   *
   * @param entity 通知实体
   * @return metadata Map
   */
  private static Map<String, Object> assembleMetadata(NotificationEntity entity) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    putIfPresent(metadata, NotificationMetadataKeys.JOB_TYPE, entity.getJobType());
    putIfPresent(metadata, NotificationMetadataKeys.MODE, entity.getModeCode());
    // 仅热点/配图/修复暴露 articleId（课程作品 ID 不进 metadata.articleId）
    if (shouldExposeArticleId(entity)) {
      putIfPresent(metadata, NotificationMetadataKeys.ARTICLE_ID, entity.getWorkId());
    }
    putIfPresent(metadata, NotificationMetadataKeys.DOCUMENT_ID, entity.getOutputId());
    putIfPresent(metadata, NotificationMetadataKeys.DRAFT_DOCUMENT_ID, entity.getDraftOutputId());
    putIfPresent(metadata, NotificationMetadataKeys.THEME_ID, entity.getThemeId());
    putIfPresent(metadata, NotificationMetadataKeys.THEME_CODE, entity.getThemeCode());
    putIfPresent(metadata, NotificationMetadataKeys.THEME_NAME, entity.getThemeName());
    putIfPresent(metadata, NotificationMetadataKeys.TOPIC_ID, entity.getTopicId());
    putIfPresent(metadata, NotificationMetadataKeys.FETCH_RUN_ID, entity.getFetchRunId());
    putIfPresent(metadata, NotificationMetadataKeys.RESEARCH_RUN_ID, entity.getResearchRunId());
    putIfPresent(metadata, NotificationMetadataKeys.COURSE_TITLE, entity.getSubjectTitle());
    putIfPresent(metadata, NotificationMetadataKeys.ARTICLE_TITLE, entity.getArticleTitle());
    putIfPresent(metadata, NotificationMetadataKeys.SERIES_NAME, entity.getSeriesName());
    if (entity.getLessonNo() != null) {
      metadata.put(NotificationMetadataKeys.LESSON_NO, entity.getLessonNo());
    }
    putIfPresent(metadata, NotificationMetadataKeys.ERROR_CODE, entity.getErrorCode());
    putIfPresent(metadata, NotificationMetadataKeys.RAW_ERROR_MESSAGE, entity.getRawErrorMessage());
    return metadata;
  }

  /**
   * 是否应将 work_id 作为 metadata.articleId 输出。
   *
   * @param entity 通知实体
   * @return 热点模式或配图/修复任务时为 true
   */
  private static boolean shouldExposeArticleId(NotificationEntity entity) {
    if (StrUtil.isBlank(entity.getWorkId())) {
      return false;
    }
    if (ContentModeIds.HOT_NEWS_CODE.equals(entity.getModeCode())) {
      return true;
    }
    String jobType = StrUtil.nullToEmpty(entity.getJobType());
    return JobTypes.IMAGE_GENERATION.equals(jobType) || JobTypes.ARTICLE_REPAIR.equals(jobType);
  }

  /**
   * 非空白字符串写入 metadata。
   *
   * @param metadata 目标 Map
   * @param key      键
   * @param value    值
   */
  private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
    if (StrUtil.isNotBlank(value)) {
      metadata.put(key, value);
    }
  }

  /**
   * LocalDateTime 转 ISO 字符串。
   *
   * @param value 时间
   * @return 字符串；空则 null
   */
  private static String dateTimeToString(LocalDateTime value) {
    return value == null ? null : value.toString();
  }
}
