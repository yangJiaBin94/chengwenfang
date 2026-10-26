package com.chengwenfang.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.app.support.ChengwenfangIT;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "chengwenfang.worker-enabled=false"
})
class CourseWorkspaceApiIT extends ChengwenfangIT {

  @Autowired
  ContentFacade contentFacade;

  @Test
  void human_review_layout_snapshot_and_skills_contract() throws Exception {
    String courseId = "ws-" + UUID.randomUUID().toString().substring(0, 8);
    CourseShellView shell = contentFacade.ensureCourseShell(courseId);
    String articleId = shell.getWechatArticleId();

    putJson("/api/articles/" + articleId, Map.of(
        "content", "# workspace article\n\nbody",
        "source", "manual_edit",
        "reason", "test"));

    Map<String, Object> review = putJson("/api/articles/" + articleId + "/human-review", Map.of(
        "status", "approved",
        "comment", "ok"));
    assertThat(review.get("articleId")).isEqualTo(articleId);
    assertThat(review.get("status")).isEqualTo("approved");
    assertThat(getJson("/api/articles/" + articleId + "/human-review").get("status")).isEqualTo("approved");

    Map<String, Object> layout = getJson("/api/articles/" + articleId + "/layout");
    assertThat(layout.get("articleId")).isEqualTo(articleId);
    Map<String, Object> savedLayout = putJson("/api/articles/" + articleId + "/layout", Map.of(
        "articleVersionNo", layout.get("articleVersionNo"),
        "themeVersionId", "theme-v1",
        "options", Map.of("fontSize", 16)));
    assertThat(savedLayout.get("themeVersionId")).isEqualTo("theme-v1");

    Map<String, Object> snapshot = postJson("/api/articles/" + articleId + "/publishing-snapshots", Map.of(
        "articleVersionNo", savedLayout.get("articleVersionNo"),
        "themeVersionId", "theme-v1",
        "options", Map.of()));
    assertThat(snapshot.get("articleId")).isEqualTo(articleId);
    assertThat(snapshot.get("html")).asString().isNotBlank();
    List<Map<String, Object>> snapshots = getJsonList("/api/articles/" + articleId + "/publishing-snapshots");
    assertThat(snapshots).isNotEmpty();

    List<Map<String, Object>> nodes = getJsonList("/api/settings/skills/workflow-nodes");
    assertThat(nodes).isNotEmpty();
    assertThat(nodes.get(0)).containsKeys("workflowType", "nodeKey", "name", "group", "modelType");
  }
}
