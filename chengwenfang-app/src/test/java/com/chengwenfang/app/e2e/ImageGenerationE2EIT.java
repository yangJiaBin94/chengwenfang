package com.chengwenfang.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.app.support.ChengwenfangIT;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "chengwenfang.worker-enabled=true",
    "chengwenfang.worker-concurrency=2"
})
class ImageGenerationE2EIT extends ChengwenfangIT {

  @Autowired
  ContentFacade content;

  @Test
  void image_generation_without_upstream_fails() throws Exception {
    String courseId = "img-course-" + UUID.randomUUID().toString().substring(0, 8);
    CourseShellView shell = content.ensureCourseShell(courseId);
    String articleId = shell.getWechatArticleId();

    Map<String, Object> job = postJson("/api/articles/" + articleId + "/images", Map.of(
        "prompt", "test cover",
        "size", "1024x1024",
        "kind", "cover",
        "sequenceNo", 1));
    assertThat(job.get("type")).isEqualTo("image_generation");
    String jobId = String.valueOf(job.get("id"));

    Map<String, Object> terminal = awaitTerminal(jobId);
    assertThat(terminal.get("status")).isEqualTo("failed");
    assertThat(String.valueOf(terminal.get("errorMessage"))).contains("配图生成失败");
  }
}
