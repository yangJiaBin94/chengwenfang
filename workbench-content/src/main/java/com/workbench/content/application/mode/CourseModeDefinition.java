package com.workbench.content.application.mode;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.content.application.WorkContentStoreImpl;
import com.workbench.content.domain.entity.CourseLessonEntity;
import com.workbench.content.domain.entity.CourseLessonOutcomeEntity;
import com.workbench.content.domain.entity.WorkEntity;
import com.workbench.content.domain.entity.WorkGenerationSettingsEntity;
import com.workbench.content.facade.constant.ChannelCodes;
import com.workbench.content.facade.constant.ContentModeIds;
import com.workbench.content.facade.constant.CourseLessonStatuses;
import com.workbench.content.facade.constant.OutputFormatCodes;
import com.workbench.content.facade.dto.DefaultOutputSpec;
import com.workbench.content.facade.dto.ModeWorkNavInfo;
import com.workbench.content.facade.dto.WorkExtensionPayload;
import com.workbench.content.facade.spi.ModeDefinition;
import com.workbench.content.infrastructure.mapper.CourseLessonMapper;
import com.workbench.content.infrastructure.mapper.CourseLessonOutcomeMapper;
import com.workbench.content.infrastructure.mapper.WorkGenerationSettingsMapper;
import com.workbench.content.infrastructure.mapper.WorkMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 课程体系模式定义：默认双轨产出（原稿/公众号）与 course_lessons / settings 扩展。
 */
@Component
@RequiredArgsConstructor
public class CourseModeDefinition implements ModeDefinition {

  /** 默认产出组合。 */
  private static final List<DefaultOutputSpec> DEFAULT_OUTPUTS = List.of(
      DefaultOutputSpec.builder()
          .channelCode(ChannelCodes.MASTER)
          .formatCode(OutputFormatCodes.ARTICLE)
          .titleSuffix(" 原稿")
          .build(),
      DefaultOutputSpec.builder()
          .channelCode(ChannelCodes.WECHAT_MP)
          .formatCode(OutputFormatCodes.ARTICLE)
          .titleSuffix(" 公众号")
          .build());

  /** 作品表。 */
  private final WorkMapper workMapper;
  /** 课次扩展。 */
  private final CourseLessonMapper lessonMapper;
  /** 生成设置。 */
  private final WorkGenerationSettingsMapper settingsMapper;
  /** 学习成果。 */
  private final CourseLessonOutcomeMapper outcomeMapper;
  /** 产出指针。 */
  private final WorkContentStoreImpl workContents;

  /**
   * {@inheritDoc}
   */
  @Override
  public String modeCode() {
    return ContentModeIds.COURSE_SYSTEM_CODE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<DefaultOutputSpec> defaultOutputs() {
    return DEFAULT_OUTPUTS;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void createExtension(WorkExtensionPayload payload) {
    if (payload == null || StrUtil.isBlank(payload.getWorkId())) {
      return;
    }
    WorkExtensionPayload.CourseLessonExtension course = payload.getCourse();
    if (course == null) {
      return;
    }
    LocalDateTime now = payload.getUpdatedAt() != null ? payload.getUpdatedAt() : LocalDateTime.now();
    // 课次行
    lessonMapper.insert(CourseLessonEntity.builder()
        .workId(payload.getWorkId())
        .seriesId(course.getSeriesId())
        .lessonNo(course.getLessonNo())
        .summary(StrUtil.nullToEmpty(course.getSummary()))
        .status(StrUtil.blankToDefault(payload.getStatus(), CourseLessonStatuses.DRAFT))
        .progress(course.getProgress() == null ? 0 : course.getProgress())
        .seriesPositioning(course.getSeriesPositioning())
        .build());
    // 生成设置行
    settingsMapper.insert(WorkGenerationSettingsEntity.builder()
        .workId(payload.getWorkId())
        .wordCountOption(StrUtil.blankToDefault(course.getWordCountOption(), "2000-4000"))
        .imageCountOption(StrUtil.blankToDefault(course.getImageCountOption(), "3-5"))
        .recommendedWordCountOption(course.getRecommendedWordCountOption())
        .recommendedImageCountOption(course.getRecommendedImageCountOption())
        .recommendationReason(course.getRecommendationReason())
        .recommendedAt(course.getRecommendedAt())
        .preferenceFingerprint(course.getPreferenceFingerprint())
        .updatedAt(now)
        .build());
    replaceOutcomes(payload.getWorkId(), course.getLearningOutcomes());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteExtension(String workId) {
    if (StrUtil.isBlank(workId)) {
      return;
    }
    outcomeMapper.delete(new LambdaQueryWrapper<CourseLessonOutcomeEntity>()
        .eq(CourseLessonOutcomeEntity::getWorkId, workId));
    settingsMapper.deleteById(workId);
    lessonMapper.deleteById(workId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ModeWorkNavInfo resolveNavInfo(String workId) {
    if (StrUtil.isBlank(workId)) {
      return null;
    }
    WorkEntity work = workMapper.selectById(workId);
    if (work == null) {
      return null;
    }
    return ModeWorkNavInfo.builder()
        .workId(workId)
        .title(StrUtil.nullToEmpty(work.getTitle()))
        .themeId(work.getThemeId())
        .draftOutputId(workContents.findOutputId(workId, ChannelCodes.MASTER))
        .wechatOutputId(workContents.findOutputId(workId, ChannelCodes.WECHAT_MP))
        .build();
  }

  /**
   * 整表替换学习成果。
   *
   * @param workId   作品 id
   * @param outcomes 成果；null/空则清空
   */
  private void replaceOutcomes(String workId, List<String> outcomes) {
    outcomeMapper.delete(new LambdaQueryWrapper<CourseLessonOutcomeEntity>()
        .eq(CourseLessonOutcomeEntity::getWorkId, workId));
    if (CollUtil.isEmpty(outcomes)) {
      return;
    }
    int seq = 0;
    for (String text : outcomes) {
      if (StrUtil.isBlank(text)) {
        continue;
      }
      seq++;
      outcomeMapper.insert(CourseLessonOutcomeEntity.builder()
          .id(IdsUtil.newId())
          .workId(workId)
          .seq(seq)
          .content(text.trim())
          .build());
    }
  }
}
