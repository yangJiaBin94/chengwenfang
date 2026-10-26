package com.workbench.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.content.domain.entity.ContentThemeEntity;
import com.workbench.content.domain.entity.CourseEntity;
import com.workbench.content.domain.entity.MediaAssetEntity;
import com.workbench.content.domain.entity.WorkEntity;
import com.workbench.content.infrastructure.mapper.ContentThemeMapper;
import com.workbench.content.infrastructure.mapper.MediaAssetMapper;
import com.workbench.content.infrastructure.mapper.WorkMapper;
import com.workbench.content.infrastructure.mapper.WorkOutputMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 已软删主题的下游展示闸门：路线、课程与文章读/写均据此拦截。
 */
@Component
@RequiredArgsConstructor
public class ThemeVisibilityGuard {
  private final ContentThemeMapper themeMapper;
  private final CourseStore courseStore;
  private final WorkMapper workMapper;
  private final WorkOutputMapper workOutputMapper;
  private final MediaAssetMapper mediaAssetMapper;

  /**
   * 主题已软删时抛 404。
   *
   * @param theme 主题实体；null 时不拦截（由调用方处理不存在）
   */
  public void assertThemeActive(ContentThemeEntity theme) {
    if (theme != null && theme.getDeletedAt() != null) {
      throw new BusinessException(ErrorCodes.THEME_DELETED, "主题已删除，请先恢复", 404);
    }
  }

  /**
   * 按主题 id 拦截已软删主题。
   *
   * @param themeId 主题 id；空则跳过
   */
  public void assertThemeActiveById(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return;
    }
    assertThemeActive(themeMapper.selectById(themeId));
  }

  /**
   * 按课程拦截所属主题。
   *
   * @param course 课程；null 时不拦截
   */
  public void assertThemeActiveForCourse(CourseEntity course) {
    if (course == null) {
      return;
    }
    assertThemeActiveById(course.getThemeId());
  }

  /**
   * 按课程 id 拦截所属主题。
   *
   * @param courseId 课程 id；空则跳过
   */
  public void assertThemeActiveForCourseId(String courseId) {
    if (StrUtil.isBlank(courseId)) {
      return;
    }
    CourseEntity course = courseStore.selectById(courseId);
    if (course != null) {
      assertThemeActiveForCourse(course);
      return;
    }
    WorkEntity work = workMapper.selectById(courseId);
    if (work != null) {
      assertThemeActiveById(work.getThemeId());
    }
  }

  /**
   * 按文章/产出/作品 id 反查再拦截所属主题。
   *
   * @param articleId 文章 id；空或不存在则跳过
   */
  public void assertThemeActiveForArticleId(String articleId) {
    if (StrUtil.isBlank(articleId)) {
      return;
    }
    var output = workOutputMapper.selectById(articleId);
    if (output != null) {
      assertThemeActiveForCourseId(output.getWorkId());
      return;
    }
    WorkEntity work = workMapper.selectById(articleId);
    if (work != null) {
      assertThemeActiveById(work.getThemeId());
    }
  }

  /**
   * 按媒资 id 反查课程再拦截所属主题。
   *
   * @param mediaId 媒资 id；空或不存在则跳过
   */
  public void assertThemeActiveForMediaId(String mediaId) {
    if (StrUtil.isBlank(mediaId)) {
      return;
    }
    MediaAssetEntity media = mediaAssetMapper.selectById(mediaId);
    if (media == null) {
      return;
    }
    assertThemeActiveForCourseId(media.getWorkId());
  }

  /**
   * 按配图 id 反查文章再拦截所属主题。
   *
   * @param imageId 配图 id；空或不存在则跳过
   */
  public void assertThemeActiveForImageId(String imageId) {
    if (StrUtil.isBlank(imageId)) {
      return;
    }
    MediaAssetEntity asset = mediaAssetMapper.selectById(imageId);
    if (asset != null) {
      assertThemeActiveForCourseId(asset.getWorkId());
    }
  }

  /**
   * 已软删主题 id 集合，供全量课表过滤。
   *
   * @return 已删主题 id
   */
  public Set<String> deletedThemeIds() {
    return themeMapper.selectList(new LambdaQueryWrapper<ContentThemeEntity>()
            .isNotNull(ContentThemeEntity::getDeletedAt)
            .select(ContentThemeEntity::getId))
        .stream()
        .map(ContentThemeEntity::getId)
        .collect(Collectors.toCollection(HashSet::new));
  }
}
