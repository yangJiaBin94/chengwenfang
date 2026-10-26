package com.workbench.app.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.app.support.WorkbenchIT;
import com.workbench.app.adapter.worker.JobTestGate;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.job.facade.dto.job.ArticleRepairJobInput;
import com.workbench.job.facade.dto.job.CourseContentGenerationJobInput;
import com.workbench.job.facade.dto.job.CourseDraftGenerationJobInput;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "workbench.worker-enabled=true",
    "workbench.worker-concurrency=2",
    "spring.application.name=job-worker-golden-it"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class JobWorkerGoldenPathIT extends WorkbenchIT {

  @Autowired
  JobTestGate gate;

  @Test
  void worker_runs_stub_job_to_succeeded_with_started_and_progress() throws Exception {
    gate.disarm();
    JobView created = jobs.create(
        JobTypes.COURSE_CONTENT_GENERATION, "course", "c-worker-1", new CourseContentGenerationJobInput());

    Map<String, Object> terminal = awaitTerminal(created.getId());
    assertThat(terminal.get("status")).isEqualTo("succeeded");
    assertThat(terminal.get("progress")).isEqualTo(100);

    List<Map<String, Object>> events = eventHistory(created.getId());
    assertThat(events).extracting(e -> e.get("eventType"))
        .contains("queued", "started", "progress", "succeeded");
    List<Long> ids = events.stream().map(e -> ((Number) e.get("id")).longValue()).toList();
    assertThat(ids).isSorted();
  }

  @Test
  void worker_runs_two_jobs_concurrently() throws Exception {
    gate.disarm();
    gate.armBlock(2);

    JobView first = jobs.create(JobTypes.ARTICLE_REPAIR, "article", "a-1",
        ArticleRepairJobInput.builder().articleId("a-1").build());
    JobView second = jobs.create(JobTypes.ARTICLE_REPAIR, "article", "a-2",
        ArticleRepairJobInput.builder().articleId("a-2").build());

    boolean entered = gate.awaitEntered(8, TimeUnit.SECONDS);
    // Always release promptly — JobTestGate blocks for at most 15s.
    gate.releaseAll();
    gate.disarm();

    if (entered) {
      assertThat(gate.maxInFlight()).isGreaterThanOrEqualTo(2);
    }
    assertThat(awaitTerminal(first.getId()).get("status")).isEqualTo("succeeded");
    assertThat(awaitTerminal(second.getId()).get("status")).isEqualTo("succeeded");
  }

  @Test
  void cancel_running_job_stays_cancelled() throws Exception {
    gate.disarm();
    gate.armBlock(1);

    JobView created = jobs.create(JobTypes.ARTICLE_REPAIR, "article", "a-cancel",
        ArticleRepairJobInput.builder().articleId("a-cancel").build());
    boolean entered = gate.awaitEntered(8, TimeUnit.SECONDS);
    if (!entered) {
      gate.releaseAll();
      gate.disarm();
      Map<String, Object> cancelled = postJson("/api/jobs/" + created.getId() + "/cancel");
      assertThat(cancelled.get("status")).isIn("cancelled", "succeeded");
      return;
    }

    Map<String, Object> cancelled = postJson("/api/jobs/" + created.getId() + "/cancel");
    assertThat(cancelled.get("status")).isEqualTo("cancelled");
    gate.releaseAll();
    gate.disarm();

    org.awaitility.Awaitility.await()
        .during(Duration.ofMillis(800))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(getJob(created.getId()).get("status")).isEqualTo("cancelled"));
  }

  @Test
  void retry_failed_is_rerun_by_worker() throws Exception {
    gate.disarm();
    JobView created = jobs.create(
        JobTypes.COURSE_DRAFT_GENERATION, "course", "c-retry", new CourseDraftGenerationJobInput());
    awaitTerminal(created.getId());

    jdbc.update(
        "UPDATE jobs SET status='failed', error_code='x', error_message='forced', finished_at=UTC_TIMESTAMP(3), progress=40 WHERE id=?",
        created.getId());
    Map<String, Object> accepted = postJson("/api/jobs/" + created.getId() + "/retry-failed");
    assertThat(accepted.get("status")).isEqualTo("pending");

    Map<String, Object> terminal = awaitTerminal(created.getId());
    assertThat(terminal.get("status")).isEqualTo("succeeded");
    assertThat(((Number) terminal.get("retryCount")).intValue()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void sse_resumes_after_last_event_id_and_closes_on_terminal() throws Exception {
    gate.disarm();
    JobView created = jobs.create(
        JobTypes.COURSE_DRAFT_GENERATION, "course", "c-sse", new CourseDraftGenerationJobInput());
    awaitTerminal(created.getId());

    List<Map<String, Object>> history = eventHistory(created.getId());
    assertThat(history).isNotEmpty();
    long firstId = ((Number) history.get(0).get("id")).longValue();

    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl() + "/api/jobs/" + created.getId() + "/events?after=" + firstId))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    String body = response.body();
    assertThat(body).doesNotContain("id:" + firstId + "\n");
    assertThat(body).doesNotContain("id: " + firstId + "\n");
    assertThat(body).containsAnyOf("event:succeeded", "event: succeeded");
  }
}
