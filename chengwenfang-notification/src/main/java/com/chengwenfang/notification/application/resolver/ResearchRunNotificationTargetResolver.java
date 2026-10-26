package com.chengwenfang.notification.application.resolver;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchRunView;
import com.chengwenfang.notification.application.support.NotificationTargetLookupSupport;
import com.chengwenfang.notification.facade.constant.NotificationMetadataKeys;
import com.chengwenfang.notification.facade.spi.NotificationTargetResolver;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 选题研究任务：调研运行 → topicId。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearchRunNotificationTargetResolver implements NotificationTargetResolver {

  /** 热点门面。 */
  private final HotNewsFacade hotNews;
  /** 共用选题补齐。 */
  private final NotificationTargetLookupSupport lookup;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supports(String jobType) {
    return JobTypes.HOT_NEWS_TOPIC_RESEARCH.equals(jobType);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String resolve(String entityId, Map<String, Object> metadata) {
    metadata.put(NotificationMetadataKeys.MODE, ContentModeIds.HOT_NEWS_CODE);
    metadata.put(NotificationMetadataKeys.RESEARCH_RUN_ID, entityId);
    Object existingTopic = metadata.get(NotificationMetadataKeys.TOPIC_ID);
    String topicId = existingTopic == null ? "" : String.valueOf(existingTopic).trim();
    if (StrUtil.isBlank(topicId)) {
      try {
        HotNewsResearchRunView run = hotNews.getResearchRun(entityId);
        topicId = StrUtil.nullToEmpty(run.getTopicId()).trim();
      } catch (Exception exception) {
        log.warn("补齐调研运行通知元数据失败 runId={} err={}", entityId, exception.toString());
      }
    }
    if (StrUtil.isNotBlank(topicId)) {
      return lookup.enrichTopic(topicId, metadata);
    }
    return "";
  }
}
