package com.workbench.notification.application.resolver;

import com.workbench.job.facade.constant.JobTypes;
import com.workbench.notification.application.support.NotificationTargetLookupSupport;
import com.workbench.notification.facade.spi.NotificationTargetResolver;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 信源视频解析：实体为主题 ID（热点模式）。
 */
@Component
@RequiredArgsConstructor
public class SourceVideoParseNotificationTargetResolver implements NotificationTargetResolver {

  /** 共用主题补齐。 */
  private final NotificationTargetLookupSupport lookup;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supports(String jobType) {
    return JobTypes.HOT_NEWS_SOURCE_VIDEO_PARSE.equals(jobType);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String resolve(String entityId, Map<String, Object> metadata) {
    return lookup.enrichTheme(entityId, metadata, true);
  }
}
