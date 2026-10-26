package com.chengwenfang.notification.application.resolver;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.notification.facade.constant.NotificationMetadataKeys;
import com.chengwenfang.notification.facade.spi.NotificationTargetResolver;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 热点文章相关任务：生成 / 改写 / 审稿 / 修复 / 复检。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotNewsArticleNotificationTargetResolver implements NotificationTargetResolver {

  /** 本解析器支持的任务类型。 */
  private static final Set<String> SUPPORTED = Set.of(
      JobTypes.HOT_NEWS_ARTICLE_GENERATION,
      JobTypes.HOT_NEWS_ARTICLE_REWRITE,
      JobTypes.HOT_ARTICLE_REVIEW,
      JobTypes.HOT_ARTICLE_REVIEW_REPAIR,
      JobTypes.HOT_ARTICLE_REVIEW_REVALIDATE);

  /** 热点门面。 */
  private final HotNewsFacade hotNews;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supports(String jobType) {
    return SUPPORTED.contains(jobType);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String resolve(String entityId, Map<String, Object> metadata) {
    metadata.put(NotificationMetadataKeys.MODE, ContentModeIds.HOT_NEWS_CODE);
    metadata.put(NotificationMetadataKeys.ARTICLE_ID, entityId);
    try {
      HotNewsArticleView article = hotNews.getHotArticle(entityId);
      if (StrUtil.isNotBlank(article.getTopicId())) {
        metadata.put(NotificationMetadataKeys.TOPIC_ID, article.getTopicId());
      }
      // 跳转深链优先走 documents；output 已有则不覆盖
      if (StrUtil.isNotBlank(article.getDocumentId())) {
        metadata.putIfAbsent(NotificationMetadataKeys.DOCUMENT_ID, article.getDocumentId());
      }
      if (StrUtil.isNotBlank(article.getDraftDocumentId())) {
        metadata.putIfAbsent(NotificationMetadataKeys.DRAFT_DOCUMENT_ID, article.getDraftDocumentId());
      }
      if (StrUtil.isNotBlank(article.getTitle())) {
        metadata.put(NotificationMetadataKeys.ARTICLE_TITLE, article.getTitle());
        metadata.putIfAbsent(NotificationMetadataKeys.COURSE_TITLE, article.getTitle());
        return article.getTitle();
      }
    } catch (Exception exception) {
      log.warn("补齐热点文章通知元数据失败 articleId={} err={}", entityId, exception.toString());
    }
    return "";
  }
}
