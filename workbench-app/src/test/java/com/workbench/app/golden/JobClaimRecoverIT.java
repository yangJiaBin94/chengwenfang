package com.workbench.app.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.app.support.WorkbenchIT;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.job.facade.dto.view.JobEventView;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.job.facade.dto.job.CourseDraftGenerationJobInput;
import com.workbench.job.facade.dto.job.JobPayloads;
import com.workbench.job.facade.dto.job.OkJobOutput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "workbench.worker-enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class JobClaimRecoverIT extends WorkbenchIT {

  @Test
  void create_claim_progress_succeed_emits_ordered_events() {
    JobView created = jobs.create(
        JobTypes.COURSE_DRAFT_GENERATION, "course", "course-1", new CourseDraftGenerationJobInput());
    assertThat(created.getStatus()).isEqualTo("pending");

    JobEntity claimed = jobs.claimNext(30);
    assertThat(claimed).isNotNull();
    assertThat(claimed.getId()).isEqualTo(created.getId());
    assertThat(claimed.getStatus()).isEqualTo("running");

    jobs.updateProgress(created.getId(), "draft", 50, "Drafting");
    jobs.succeed(created.getId(), JobPayloads.okTrue());

    JobView stored = jobs.get(created.getId());
    assertThat(stored.getStatus()).isEqualTo("succeeded");
    assertThat(stored.getProgress()).isEqualTo(100);

    List<JobEventView> events = jobs.eventsAfter(created.getId(), 0);
    assertThat(events).extracting(JobEventView::getEventType)
        .containsExactly("queued", "progress", "succeeded");
    assertThat(events).extracting(JobEventView::getId)
        .isSorted();
  }

  @Test
  void expired_running_job_is_reclaimed_with_recovery_event() {
    JobView created = jobs.create(
        JobTypes.COURSE_DRAFT_GENERATION, "course", "course-2", new CourseDraftGenerationJobInput());
    JobEntity claimed = jobs.claimNext(30);
    assertThat(claimed).isNotNull();

    expireLease(created.getId());

    JobEntity reclaimed = jobs.claimNext(30);
    assertThat(reclaimed).isNotNull();
    assertThat(reclaimed.getId()).isEqualTo(created.getId());
    assertThat(reclaimed.getStatus()).isEqualTo("running");
    assertThat(reclaimed.getRetryCount()).isEqualTo(1);

    List<JobEventView> events = jobs.eventsAfter(created.getId(), 0);
    assertThat(events.get(events.size() - 1).getEventType()).isEqualTo("warning");
    assertThat(events.get(events.size() - 1).getStep()).isEqualTo("recovered");
  }

  @Test
  void terminal_progress_is_ignored_after_success() {
    JobView created = jobs.create(
        JobTypes.COURSE_DRAFT_GENERATION, "course", "course-3", new CourseDraftGenerationJobInput());
    jobs.claimNext(30);
    jobs.succeed(created.getId(), OkJobOutput.builder().build());

    jobs.updateProgress(created.getId(), "late", 99, "should be ignored");
    jobs.succeed(created.getId(), Map.of("again", true));

    JobView stored = jobs.get(created.getId());
    assertThat(stored.getStatus()).isEqualTo("succeeded");
    assertThat(stored.getProgress()).isEqualTo(100);
    assertThat(jobs.eventsAfter(created.getId(), 0))
        .extracting(JobEventView::getEventType)
        .doesNotContain("progress")
        .containsOnlyOnce("succeeded");
  }

  @Test
  void retry_failed_requeues_job() {
    JobView created = jobs.create(
        JobTypes.COURSE_DRAFT_GENERATION, "course", "course-4", new CourseDraftGenerationJobInput());
    jobs.claimNext(30);
    jobs.fail(created.getId(), "boom", "forced failure");

    JobView retried = jobs.retryFailed(created.getId());
    assertThat(retried.getStatus()).isEqualTo("pending");
    assertThat(retried.getRetryCount()).isEqualTo(1);
    assertThat(retried.getErrorCode()).isNull();

    JobEntity claimed = jobs.claimNext(30);
    assertThat(claimed).isNotNull();
    jobs.succeed(claimed.getId(), JobPayloads.okTrue());
    assertThat(jobs.get(created.getId()).getStatus()).isEqualTo("succeeded");
  }
}
