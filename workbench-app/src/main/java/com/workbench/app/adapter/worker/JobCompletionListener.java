package com.workbench.app.adapter.worker;

import cn.hutool.core.util.StrUtil;
import com.workbench.notification.application.support.JobTerminalNotificationSupport;
import com.workbench.common.JobFailureMessages;
import com.workbench.job.facade.dto.view.JobTerminalEvent;
import com.workbench.job.facade.enums.JobStatus;
import com.workbench.notification.facade.NotificationFacade;
import com.workbench.notification.facade.constant.NotificationMetadataKeys;
import com.workbench.skill.facade.WorkflowFacade;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Job 完成后的联动：工作流运行终态、站内通知推送。 */
@Slf4j
@Component
public class JobCompletionListener {
  /** 任务出参中允许带入通知结构化字段的键。 */
  private static final List<String> OUTPUT_METADATA_KEYS = List.of(
      NotificationMetadataKeys.JOB_TYPE,
      NotificationMetadataKeys.MODE,
      NotificationMetadataKeys.ARTICLE_ID,
      NotificationMetadataKeys.DOCUMENT_ID,
      NotificationMetadataKeys.DRAFT_DOCUMENT_ID,
      NotificationMetadataKeys.THEME_ID,
      NotificationMetadataKeys.THEME_CODE,
      NotificationMetadataKeys.THEME_NAME,
      NotificationMetadataKeys.TOPIC_ID,
      NotificationMetadataKeys.FETCH_RUN_ID,
      NotificationMetadataKeys.RESEARCH_RUN_ID,
      NotificationMetadataKeys.COURSE_TITLE,
      NotificationMetadataKeys.ARTICLE_TITLE,
      NotificationMetadataKeys.SERIES_NAME,
      NotificationMetadataKeys.LESSON_NO);

  /** 站内通知门面。 */
  private final NotificationFacade notifications;
  /** 工作流门面（标记运行终态）。 */
  private final WorkflowFacade workflows;
  /** 终态通知标题与 metadata 补齐。 */
  private final JobTerminalNotificationSupport notificationSupport;

  /**
   * @param notifications        通知门面
   * @param workflows            工作流门面
   * @param notificationSupport  终态通知辅助
   */
  public JobCompletionListener(
      NotificationFacade notifications,
      WorkflowFacade workflows,
      JobTerminalNotificationSupport notificationSupport) {
    this.notifications = notifications;
    this.workflows = workflows;
    this.notificationSupport = notificationSupport;
  }

  /**
   * 任务进入终态后：标记关联工作流运行结束；为所有任务类型推送站内通知。
   * <p>在事务提交后执行；子步骤失败仅打日志，不回滚任务终态。
   *
   * @param event 任务终态事件；null 直接忽略
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onJobTerminal(JobTerminalEvent event) {
    if (event == null) {
      return;
    }
    log.info("任务终态 id={} type={} status={}", event.getJobId(), event.getType(), event.getStatus());
    // 同步工作流运行终态（失败不阻断通知）
    try {
      workflows.markJobRunTerminal(event.getJobId(), event.getStatus());
    } catch (Exception exception) {
      log.warn("标记工作流运行为终态失败 jobId={} err={}", event.getJobId(), exception.toString());
    }
    // 全部 Job 类型终态均推送站内通知
    try {
      boolean succeeded = JobStatus.SUCCEEDED.getValue().equals(event.getStatus());
      Map<String, Object> metadata = new LinkedHashMap<>();
      // 仅拷贝盘点过的结构化键，不再整包落库任务出参
      copyKnownOutputKeys(event.getOutput(), metadata);
      if (event.getErrorCode() != null) {
        metadata.put(NotificationMetadataKeys.ERROR_CODE, event.getErrorCode());
      }
      if (!succeeded && StrUtil.isNotBlank(event.getErrorMessage())) {
        metadata.put(NotificationMetadataKeys.RAW_ERROR_MESSAGE, event.getErrorMessage());
      }
      String displayName = notificationSupport.enrichMetadata(
          event.getType(), event.getEntityId(), metadata);
      String title = notificationSupport.titleFor(event.getType(), succeeded, displayName);
      String message = succeeded
          ? notificationSupport.successMessage(event.getType())
          : JobFailureMessages.toUserMessage(event.getErrorMessage());
      notifications.createForTerminalJob(
          event.getType(),
          event.getJobId(),
          event.getEntityId(),
          event.getStatus(),
          title,
          message,
          metadata);
    } catch (Exception exception) {
      log.warn("创建终态通知失败 jobId={} err={}", event.getJobId(), exception.toString());
    }
  }

  /**
   * 从任务出参拷贝通知盘点键。
   *
   * @param output   任务出参；可空
   * @param metadata 目标 Map
   */
  private static void copyKnownOutputKeys(Map<String, Object> output, Map<String, Object> metadata) {
    if (output == null || output.isEmpty()) {
      return;
    }
    for (String key : OUTPUT_METADATA_KEYS) {
      if (output.containsKey(key) && output.get(key) != null) {
        metadata.put(key, output.get(key));
      }
    }
  }
}
