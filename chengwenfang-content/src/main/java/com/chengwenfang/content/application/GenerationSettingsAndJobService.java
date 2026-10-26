package com.chengwenfang.content.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.FlexiblePayload;
import com.chengwenfang.content.domain.entity.CourseEntity;
import com.chengwenfang.content.facade.dto.request.CourseGenerationSettingsRequest;
import com.chengwenfang.content.facade.dto.view.CourseGenerationSettingsView;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobEntityTypes;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.view.JobView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程生成偏好与生成/配图任务入队（偏好委派 {@link CourseGenerationSettingsService}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationSettingsAndJobService {
  /** 课程生成偏好。 */
  private final CourseGenerationSettingsService generationSettings;
  /** 任务门面。 */
  private final JobFacade jobs;
  /** 实体工具。 */
  private final ContentEntityUtil support;
  /** 视图映射。 */
  private final ContentViewMapper views;
  /** 主题可见性。 */
  private final ThemeVisibilityGuard visibility;
  /** 产出身份解析（配图 documentId）。 */
  private final WorkContentStoreImpl workContents;

  /**
   * 读取课程生成偏好（字数/配图档位）；无有效档位时返回默认。
   *
   * @param courseId 课程 id
   * @return 偏好视图
   */
  public CourseGenerationSettingsView getGenerationSettings(String courseId) {
    return generationSettings.getGenerationSettings(courseId);
  }

  /**
   * 保存课程生成偏好到 work_generation_settings。
   *
   * @param courseId 课程 id
   * @param body     偏好请求；null 表示仅确保默认档位
   * @return 保存后的偏好视图
   */
  @Transactional
  public CourseGenerationSettingsView putGenerationSettings(
      String courseId, CourseGenerationSettingsRequest body) {
    return generationSettings.putGenerationSettings(courseId, body);
  }

  /**
   * 列出课程内容生成任务，可按 courseId 过滤。
   *
   * @param courseId 可选课程 id；空则不过滤
   * @return 最近至多 100 条该类型任务
   */
  public List<JobView> listCourseGenerationJobs(String courseId) {
    return jobs.listByType(JobTypes.COURSE_CONTENT_GENERATION, 100).stream()
        .filter(job -> courseId == null || courseId.isBlank() || courseId.equals(job.getEntityId()))
        .map(views::jobView)
        .toList();
  }

  /**
   * 创建异步生成任务，返回 jobId。
   *
   * @param type     任务类型（须在 {@link JobTypes#ALL} 内）
   * @param entityId 关联实体 id（课程或文章）
   * @param input    任务入参；null 按空载荷
   * @return 新建任务 id
   */
  public String createGenerationJob(String type, String entityId, FlexiblePayload input) {
    if (!JobTypes.ALL.contains(type)) {
      throw new BusinessException(ErrorCodes.UNSUPPORTED_JOB_TYPE, type, 400);
    }
    String entityType;
    if (JobTypes.COURSE_CONTENT_GENERATION.equals(type)) {
      entityType = JobEntityTypes.COURSE;
    } else if (JobTypes.THEME_CURRICULUM_GENERATION.equals(type)) {
      entityType = JobEntityTypes.THEME;
    } else {
      entityType = JobEntityTypes.ARTICLE;
    }
    if (JobTypes.COURSE_CONTENT_GENERATION.equals(type)) {
      visibility.assertThemeActiveForCourseId(entityId);
    } else if (JobTypes.THEME_CURRICULUM_GENERATION.equals(type)) {
      visibility.assertThemeActiveById(entityId);
    } else {
      visibility.assertThemeActiveForArticleId(entityId);
    }
    String jobId = jobs.create(type, entityType, entityId, input).getId();
    log.info("创建生成任务 id={} type={} entity={}/{}", jobId, type, entityType, entityId);
    return jobId;
  }

  /**
   * 入队配图生成任务。
   *
   * @param articleId 文章 id
   * @param input     任务入参；会补 articleId / documentId
   * @return 新建任务 id
   */
  public String enqueueImageJob(String articleId, FlexiblePayload input) {
    visibility.assertThemeActiveForArticleId(articleId);
    support.requireArticle(articleId);
    FlexiblePayload body = FlexiblePayload.of(FlexiblePayload.ofNullable(input).mutableMap());
    body.putIfAbsent("articleId", articleId);
    // 显式 documentId 优先；否则按文章解析后写入任务载荷，供落库绑定
    Object rawDocumentId = body.asMap().get("documentId");
    String explicitDocumentId = rawDocumentId == null ? null : String.valueOf(rawDocumentId);
    String documentId = StrUtil.blankToDefault(
        explicitDocumentId,
        workContents.resolvePublishingOutputId(articleId));
    if (StrUtil.isNotBlank(documentId)) {
      body.put("documentId", documentId);
    }
    String jobId = jobs.create(
            JobTypes.IMAGE_GENERATION, JobEntityTypes.ARTICLE, articleId, body)
        .getId();
    log.info("配图任务已入队 id={} articleId={} documentId={}", jobId, articleId, documentId);
    return jobId;
  }

  /**
   * 构造默认生成偏好视图。
   *
   * @param courseId 课程 id
   * @return 默认档位偏好
   */
  public CourseGenerationSettingsView defaultGenerationSettings(String courseId) {
    return generationSettings.defaultGenerationSettings(courseId);
  }

  /**
   * 将请求体中的非空字段合并进课程实体。
   *
   * @param course 待更新课程
   * @param body   请求体；null 则直接返回
   */
  public void applyGenerationSettingsBody(CourseEntity course, CourseGenerationSettingsRequest body) {
    generationSettings.applyGenerationSettingsBody(course, body);
  }

  /**
   * 将课程行上的偏好字段转为对外视图。
   *
   * @param course 课程实体
   * @return 偏好视图
   */
  public CourseGenerationSettingsView generationSettingsView(CourseEntity course) {
    return generationSettings.generationSettingsView(course);
  }
}
