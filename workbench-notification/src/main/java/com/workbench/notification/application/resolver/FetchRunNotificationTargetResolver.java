package com.workbench.notification.application.resolver;

import cn.hutool.core.util.StrUtil;
import com.workbench.content.facade.constant.ContentModeIds;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.notification.application.support.NotificationTargetLookupSupport;
import com.workbench.notification.facade.constant.NotificationMetadataKeys;
import com.workbench.notification.facade.spi.NotificationTargetResolver;
import com.workbench.sourcing.facade.dto.view.HotNewsFetchRunView;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 雷达采集任务：抓取运行 → themeId。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchRunNotificationTargetResolver implements NotificationTargetResolver {

  /** 热点门面。 */
  private final HotNewsFacade hotNews;
  /** 共用主题补齐。 */
  private final NotificationTargetLookupSupport lookup;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supports(String jobType) {
    return JobTypes.HOT_NEWS_FETCH.equals(jobType);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String resolve(String entityId, Map<String, Object> metadata) {
    metadata.put(NotificationMetadataKeys.MODE, ContentModeIds.HOT_NEWS_CODE);
    metadata.put(NotificationMetadataKeys.FETCH_RUN_ID, entityId);
    // 成功输出里可能已有 themeId
    Object existingTheme = metadata.get(NotificationMetadataKeys.THEME_ID);
    String themeId = existingTheme == null ? "" : String.valueOf(existingTheme).trim();
    if (StrUtil.isBlank(themeId)) {
      try {
        HotNewsFetchRunView run = hotNews.getFetchRun(entityId);
        themeId = StrUtil.nullToEmpty(run.getThemeId()).trim();
      } catch (Exception exception) {
        log.warn("补齐抓取运行通知元数据失败 runId={} err={}", entityId, exception.toString());
      }
    }
    if (StrUtil.isNotBlank(themeId)) {
      return lookup.enrichTheme(themeId, metadata, true);
    }
    return "";
  }
}
