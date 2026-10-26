package com.workbench.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.workbench.app.support.WorkbenchIT;
import com.workbench.job.facade.constant.JobEntityTypes;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.job.facade.dto.job.ArticleRepairJobInput;
import com.workbench.job.facade.dto.job.CourseContentGenerationJobInput;
import com.workbench.job.facade.dto.job.ThemeCurriculumGenerationJobInput;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.job.facade.enums.JobStatus;
import com.workbench.notification.facade.NotificationFacade;
import com.workbench.notification.facade.constant.NotificationTypes;
import com.workbench.notification.facade.dto.view.NotificationView;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "workbench.worker-enabled=true",
    "workbench.worker-concurrency=2"
})
class NotificationPersistenceIT extends WorkbenchIT {

  @Autowired
  NotificationFacade notificationFacade;

  @Test
  void course_generation_terminal_keeps_legacy_notification_type() {
    String courseId = "notify-" + UUID.randomUUID().toString().substring(0, 8);
    // 不依赖本机 API Key：终态无论成败，通知 type 仍走课程旧码
    JobView created = jobs.create(
        JobTypes.COURSE_CONTENT_GENERATION,
        JobEntityTypes.COURSE,
        courseId,
        new CourseContentGenerationJobInput());
    Map<String, Object> terminal = awaitTerminal(created.getId());
    String status = String.valueOf(terminal.get("status"));
    assertThat(status).isIn(JobStatus.SUCCEEDED.getValue(), JobStatus.FAILED.getValue());
    String expectedType = JobStatus.SUCCEEDED.getValue().equals(status)
        ? NotificationTypes.COURSE_GENERATION_SUCCEEDED
        : NotificationTypes.COURSE_GENERATION_FAILED;

    await().atMost(10, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() ->
        notificationFacade.list("all", 50).getItems().stream()
            .anyMatch(item -> created.getId().equals(item.getJobId())));

    assertThat(notificationFacade.list("all", 50).getItems()).anySatisfy(item -> {
      assertThat(item.getJobId()).isEqualTo(created.getId());
      assertThat(item.getType()).isEqualTo(expectedType);
      assertThat(item.getStatus()).isEqualTo("unread");
    });
  }

  @Test
  void non_whitelist_job_success_creates_job_terminal_notification() {
    String articleId = "notify-repair-" + UUID.randomUUID().toString().substring(0, 8);
    // 不存在的文章走修复桩并成功，覆盖非课程/热点成文类型
    JobView created = jobs.create(
        JobTypes.ARTICLE_REPAIR,
        JobEntityTypes.ARTICLE,
        articleId,
        ArticleRepairJobInput.builder().articleId(articleId).build());
    Map<String, Object> terminal = awaitTerminal(created.getId());
    assertThat(terminal.get("status")).isEqualTo(JobStatus.SUCCEEDED.getValue());

    await().atMost(10, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() ->
        notificationFacade.list("all", 50).getItems().stream()
            .anyMatch(item -> created.getId().equals(item.getJobId())));

    assertThat(notificationFacade.list("all", 50).getItems()).anySatisfy(item -> {
      assertThat(item.getJobId()).isEqualTo(created.getId());
      assertThat(item.getType()).isEqualTo(NotificationTypes.JOB_TERMINAL_SUCCEEDED);
      assertThat(item.getLevel()).isEqualTo("success");
      assertThat(item.getStatus()).isEqualTo("unread");
    });
  }

  @Test
  void non_whitelist_job_failure_creates_job_terminal_notification() {
    String themeId = "notify-theme-" + UUID.randomUUID().toString().substring(0, 8);
    // 不存在的主题课表生成应失败
    JobView created = jobs.create(
        JobTypes.THEME_CURRICULUM_GENERATION,
        JobEntityTypes.THEME,
        themeId,
        ThemeCurriculumGenerationJobInput.builder().themeId(themeId).build());
    Map<String, Object> terminal = awaitTerminal(created.getId());
    assertThat(terminal.get("status")).isEqualTo(JobStatus.FAILED.getValue());

    await().atMost(10, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() ->
        notificationFacade.list("all", 50).getItems().stream()
            .anyMatch(item -> created.getId().equals(item.getJobId())));

    assertThat(notificationFacade.list("all", 50).getItems()).anySatisfy(item -> {
      assertThat(item.getJobId()).isEqualTo(created.getId());
      assertThat(item.getType()).isEqualTo(NotificationTypes.JOB_TERMINAL_FAILED);
      assertThat(item.getLevel()).isEqualTo("error");
    });
  }

  @Test
  void createForTerminalJob_is_idempotent_by_dedupe_key() {
    String jobId = "dedupe-" + UUID.randomUUID().toString().substring(0, 8);
    NotificationView first = notificationFacade.createForTerminalJob(
        JobTypes.ARTICLE_REPAIR,
        jobId,
        "entity-1",
        JobStatus.SUCCEEDED.getValue(),
        "文章修复完成",
        "文章修复已完成",
        Map.of("jobType", JobTypes.ARTICLE_REPAIR));
    NotificationView second = notificationFacade.createForTerminalJob(
        JobTypes.ARTICLE_REPAIR,
        jobId,
        "entity-1",
        JobStatus.SUCCEEDED.getValue(),
        "文章修复完成",
        "文章修复已完成",
        Map.of("jobType", JobTypes.ARTICLE_REPAIR));
    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(second.getType()).isEqualTo(NotificationTypes.JOB_TERMINAL_SUCCEEDED);
    long count = notificationFacade.list("all", 50).getItems().stream()
        .filter(item -> jobId.equals(item.getJobId()))
        .count();
    assertThat(count).isEqualTo(1);
  }
}
