package com.workbench.notification.application.resolver;

import com.workbench.job.facade.constant.JobTypes;
import com.workbench.notification.application.support.NotificationTargetLookupSupport;
import com.workbench.notification.facade.spi.NotificationTargetResolver;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 资料池视频解析：实体为选题 ID。
 */
@Component
@RequiredArgsConstructor
public class TopicVideoParseNotificationTargetResolver implements NotificationTargetResolver {

  /** 共用选题补齐。 */
  private final NotificationTargetLookupSupport lookup;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supports(String jobType) {
    return JobTypes.HOT_NEWS_VIDEO_PARSE.equals(jobType);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String resolve(String entityId, Map<String, Object> metadata) {
    return lookup.enrichTopic(entityId, metadata);
  }
}
