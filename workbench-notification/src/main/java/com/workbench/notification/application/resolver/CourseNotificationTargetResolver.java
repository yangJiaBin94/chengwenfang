package com.workbench.notification.application.resolver;

import com.workbench.job.facade.constant.JobTypes;
import com.workbench.notification.application.support.NotificationTargetLookupSupport;
import com.workbench.notification.facade.spi.NotificationTargetResolver;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 课程内容 / 草稿 / 公众号文章生成：实体为课程 ID。
 */
@Component
@RequiredArgsConstructor
public class CourseNotificationTargetResolver implements NotificationTargetResolver {

  /** 本解析器支持的任务类型。 */
  private static final Set<String> SUPPORTED = Set.of(
      JobTypes.COURSE_CONTENT_GENERATION,
      JobTypes.COURSE_DRAFT_GENERATION,
      JobTypes.WECHAT_ARTICLE_GENERATION);

  /** 共用课程补齐。 */
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
    return lookup.enrichCourse(entityId, metadata);
  }
}
