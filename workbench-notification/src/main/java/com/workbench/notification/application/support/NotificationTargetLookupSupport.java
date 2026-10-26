package com.workbench.notification.application.support;

import cn.hutool.core.util.StrUtil;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.constant.ContentModeIds;
import com.workbench.content.facade.dto.view.ArticleView;
import com.workbench.content.facade.dto.view.ContentThemeView;
import com.workbench.content.facade.dto.view.CourseView;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.workbench.notification.facade.constant.NotificationMetadataKeys;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知目标解析共用查询：主题 / 选题 / 课程 / 课程文章。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTargetLookupSupport {

  /** 内容门面。 */
  private final ContentFacade content;
  /** 热点门面。 */
  private final HotNewsFacade hotNews;

  /**
   * 补齐选题 metadata。
   *
   * @param topicId  选题 ID
   * @param metadata 可变 metadata
   * @return 选题标题或空串
   */
  public String enrichTopic(String topicId, Map<String, Object> metadata) {
    metadata.put(NotificationMetadataKeys.MODE, ContentModeIds.HOT_NEWS_CODE);
    metadata.put(NotificationMetadataKeys.TOPIC_ID, topicId);
    try {
      HotNewsTopicView topic = hotNews.getTopic(topicId);
      if (StrUtil.isNotBlank(topic.getThemeId())) {
        enrichTheme(topic.getThemeId(), metadata, true);
      }
      if (StrUtil.isNotBlank(topic.getTitle())) {
        metadata.putIfAbsent(NotificationMetadataKeys.COURSE_TITLE, topic.getTitle());
        return topic.getTitle();
      }
    } catch (Exception exception) {
      log.warn("补齐选题通知元数据失败 topicId={} err={}", topicId, exception.toString());
    }
    return "";
  }

  /**
   * 补齐主题 metadata。
   *
   * @param themeId     主题 ID
   * @param metadata    可变 metadata
   * @param hotNewsMode 是否热点模式
   * @return 主题名或空串
   */
  public String enrichTheme(String themeId, Map<String, Object> metadata, boolean hotNewsMode) {
    if (hotNewsMode) {
      metadata.put(NotificationMetadataKeys.MODE, ContentModeIds.HOT_NEWS_CODE);
    }
    metadata.put(NotificationMetadataKeys.THEME_ID, themeId);
    try {
      ContentThemeView theme = content.getTheme(themeId);
      if (StrUtil.isNotBlank(theme.getCode())) {
        metadata.put(NotificationMetadataKeys.THEME_CODE, theme.getCode());
      }
      if (StrUtil.isNotBlank(theme.getName())) {
        metadata.put(NotificationMetadataKeys.THEME_NAME, theme.getName());
        metadata.putIfAbsent(NotificationMetadataKeys.COURSE_TITLE, theme.getName());
        return theme.getName();
      }
    } catch (Exception exception) {
      log.warn("补齐主题通知元数据失败 themeId={} err={}", themeId, exception.toString());
    }
    return "";
  }

  /**
   * 补齐课程 metadata。
   *
   * @param courseId 课程 ID
   * @param metadata 可变 metadata
   * @return 课程标题或空串
   */
  public String enrichCourse(String courseId, Map<String, Object> metadata) {
    try {
      CourseView course = content.getCourse(courseId);
      if (StrUtil.isNotBlank(course.getTitle())) {
        metadata.put(NotificationMetadataKeys.COURSE_TITLE, course.getTitle());
      }
      if (StrUtil.isNotBlank(course.getSeries())) {
        metadata.put(NotificationMetadataKeys.SERIES_NAME, course.getSeries());
      }
      if (course.getLessonNo() != null) {
        metadata.put(NotificationMetadataKeys.LESSON_NO, course.getLessonNo());
      }
      if (StrUtil.isNotBlank(course.getThemeId())) {
        enrichTheme(course.getThemeId(), metadata, false);
      }
      // 优先读 CourseView 已投影的文档指针
      if (StrUtil.isNotBlank(course.getCurrentWechatDocumentId())) {
        metadata.putIfAbsent(NotificationMetadataKeys.DOCUMENT_ID, course.getCurrentWechatDocumentId());
      }
      if (StrUtil.isNotBlank(course.getCurrentDraftDocumentId())) {
        metadata.putIfAbsent(NotificationMetadataKeys.DRAFT_DOCUMENT_ID, course.getCurrentDraftDocumentId());
      }
      return StrUtil.nullToEmpty(course.getTitle()).trim();
    } catch (Exception exception) {
      log.warn("补齐课程通知元数据失败 courseId={} err={}", courseId, exception.toString());
      return "";
    }
  }

  /**
   * 课程文章（配图/修复）→ 反查课程与主题。
   *
   * @param articleId 文章 ID
   * @param metadata  可变 metadata
   * @return 文章或课程标题
   */
  public String enrichCourseArticle(String articleId, Map<String, Object> metadata) {
    metadata.put(NotificationMetadataKeys.ARTICLE_ID, articleId);
    Object outputArticle = metadata.get(NotificationMetadataKeys.ARTICLE_ID);
    String resolvedArticleId = outputArticle == null ? articleId : String.valueOf(outputArticle).trim();
    try {
      ArticleView article = content.getArticle(resolvedArticleId);
      if (StrUtil.isNotBlank(article.getDocumentId())) {
        metadata.putIfAbsent(NotificationMetadataKeys.DOCUMENT_ID, article.getDocumentId());
      }
      if (StrUtil.isNotBlank(article.getTitle())) {
        metadata.putIfAbsent(NotificationMetadataKeys.COURSE_TITLE, article.getTitle());
      }
      if (StrUtil.isNotBlank(article.getCourseId())) {
        String courseTitle = enrichCourse(article.getCourseId(), metadata);
        if (StrUtil.isNotBlank(article.getTitle())) {
          return article.getTitle();
        }
        return courseTitle;
      }
      return StrUtil.nullToEmpty(article.getTitle()).trim();
    } catch (Exception exception) {
      log.warn("补齐课程文章通知元数据失败 articleId={} err={}", resolvedArticleId, exception.toString());
      return enrichCourse(articleId, metadata);
    }
  }
}
