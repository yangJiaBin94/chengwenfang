package com.chengwenfang.notification.application.resolver;

import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.notification.application.support.NotificationTargetLookupSupport;
import com.chengwenfang.notification.facade.spi.NotificationTargetResolver;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 配图生成 / 文章修复：实体为课程文章 ID。
 */
@Component
@RequiredArgsConstructor
public class CourseArticleNotificationTargetResolver implements NotificationTargetResolver {

  /** 本解析器支持的任务类型。 */
  private static final Set<String> SUPPORTED = Set.of(
      JobTypes.IMAGE_GENERATION,
      JobTypes.ARTICLE_REPAIR);

  /** 共用课程文章补齐。 */
  private final NotificationTargetLookupSupport lookup;

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
    return lookup.enrichCourseArticle(entityId, metadata);
  }
}
