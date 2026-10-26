package com.chengwenfang.notification.application.resolver;

import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.notification.application.support.NotificationTargetLookupSupport;
import com.chengwenfang.notification.facade.spi.NotificationTargetResolver;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 课表生成：实体为主题 ID（课程模式）。
 */
@Component
@RequiredArgsConstructor
public class ThemeCurriculumNotificationTargetResolver implements NotificationTargetResolver {

  /** 共用主题补齐。 */
  private final NotificationTargetLookupSupport lookup;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supports(String jobType) {
    return JobTypes.THEME_CURRICULUM_GENERATION.equals(jobType);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String resolve(String entityId, Map<String, Object> metadata) {
    return lookup.enrichTheme(entityId, metadata, false);
  }
}
