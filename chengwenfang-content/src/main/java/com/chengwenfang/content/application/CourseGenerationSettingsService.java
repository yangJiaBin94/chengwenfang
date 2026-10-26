package com.chengwenfang.content.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.domain.entity.CourseEntity;
import com.chengwenfang.content.facade.dto.request.CourseGenerationSettingsRequest;
import com.chengwenfang.content.facade.dto.view.CourseGenerationSettingsView;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程生成偏好读写（字数/配图档位，落在 work_generation_settings）。
 */
@Service
@RequiredArgsConstructor
public class CourseGenerationSettingsService {
  /** 课程作品存储。 */
  private final CourseStore courseStore;
  /** 实体工具。 */
  private final ContentEntityUtil support;
  /** 主题可见性。 */
  private final ThemeVisibilityGuard visibility;

  /**
   * 读取课程生成偏好（字数/配图档位）；无有效档位时返回默认。
   *
   * @param courseId 课程 id
   * @return 偏好视图
   */
  public CourseGenerationSettingsView getGenerationSettings(String courseId) {
    visibility.assertThemeActiveForCourseId(courseId);
    CourseEntity course = support.requireCourse(courseId);
    if (StrUtil.isBlank(course.getWordCountOption())) {
      return defaultGenerationSettings(courseId);
    }
    return generationSettingsView(course);
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
    visibility.assertThemeActiveForCourseId(courseId);
    CourseEntity course = support.requireCourse(courseId);
    LocalDateTime now = LocalDateTime.now();
    if (StrUtil.isBlank(course.getWordCountOption())) {
      course.setWordCountOption("2000-4000");
      course.setImageCountOption("3-5");
      course.setRecommendedWordCountOption("2000-4000");
      course.setRecommendedImageCountOption("3-5");
      course.setRecommendationReason("");
      course.setRecommendedAt(now);
    }
    if (body != null) {
      applyGenerationSettingsBody(course, body);
    }
    course.setUpdatedAt(now);
    courseStore.updateById(course);
    CourseGenerationSettingsView view = generationSettingsView(course);
    view.setFromCache(false);
    return view;
  }

  /**
   * 构造默认生成偏好视图。
   *
   * @param courseId 课程 id
   * @return 默认档位偏好
   */
  public CourseGenerationSettingsView defaultGenerationSettings(String courseId) {
    return CourseGenerationSettingsView.builder()
        .courseId(courseId)
        .wordCountOption("2000-4000")
        .imageCountOption("3-5")
        .recommendedWordCountOption("2000-4000")
        .recommendedImageCountOption("3-5")
        .recommendationReason("")
        .recommendedAt(LocalDateTime.now().toString())
        .fromCache(false)
        .build();
  }

  /**
   * 将请求体中的非空字段合并进课程实体。
   *
   * @param course 待更新课程
   * @param body   请求体；null 则直接返回
   */
  public void applyGenerationSettingsBody(CourseEntity course, CourseGenerationSettingsRequest body) {
    if (body == null) {
      return;
    }
    if (body.getWordCountOption() != null) {
      course.setWordCountOption(body.getWordCountOption());
    }
    if (body.getImageCountOption() != null) {
      course.setImageCountOption(body.getImageCountOption());
    }
    if (body.getRecommendedWordCountOption() != null) {
      course.setRecommendedWordCountOption(body.getRecommendedWordCountOption());
    }
    if (body.getRecommendedImageCountOption() != null) {
      course.setRecommendedImageCountOption(body.getRecommendedImageCountOption());
    }
    if (body.getRecommendationReason() != null) {
      course.setRecommendationReason(body.getRecommendationReason());
    }
  }

  /**
   * 将课程行上的偏好字段转为对外视图。
   *
   * @param course 课程实体
   * @return 偏好视图
   */
  public CourseGenerationSettingsView generationSettingsView(CourseEntity course) {
    return CourseGenerationSettingsView.builder()
        .courseId(course.getId())
        .wordCountOption(StrUtil.blankToDefault(course.getWordCountOption(), "2000-4000"))
        .imageCountOption(StrUtil.blankToDefault(course.getImageCountOption(), "3-5"))
        .recommendedWordCountOption(course.getRecommendedWordCountOption())
        .recommendedImageCountOption(course.getRecommendedImageCountOption())
        .recommendationReason(
            course.getRecommendationReason() == null ? "" : course.getRecommendationReason())
        .recommendedAt(
            course.getRecommendedAt() == null ? null : course.getRecommendedAt().toString())
        .fromCache(true)
        .build();
  }
}
