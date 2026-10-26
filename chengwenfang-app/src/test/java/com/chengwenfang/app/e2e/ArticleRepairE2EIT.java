package com.chengwenfang.app.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.app.support.ChengwenfangIT;
import com.chengwenfang.content.facade.dto.request.ArticleUpdateRequest;
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
class ArticleRepairE2EIT extends ChengwenfangIT {

  @Autowired
  ContentFacade content;

  @Test
  void article_repair_removes_forbidden_arrow_or_improves_score() throws Exception {
    String courseId = "repair-course-" + UUID.randomUUID().toString().substring(0, 8);
    CourseShellView shell = content.ensureCourseShell(courseId);
    String articleId = shell.getWechatArticleId();

    content.updateArticle(articleId, ArticleUpdateRequest.builder()
        .content("## 行动\n路径 -> 结果\n本课小结\n- ok\n下节课预告\n- next")
        .build());

    Map<String, Object> beforeAudit = postJson("/api/articles/" + articleId + "/audit");
    int beforeScore = ((Number) beforeAudit.get("score")).intValue();

    Map<String, Object> job = postJson("/api/articles/" + articleId + "/repair");
    assertThat(job.get("type")).isEqualTo("article_repair");
    Map<String, Object> terminal = awaitTerminal(String.valueOf(job.get("id")));
    assertThat(terminal.get("status")).isEqualTo("succeeded");

    @SuppressWarnings("unchecked")
    Map<String, Object> output = (Map<String, Object>) terminal.get("output");
    assertThat(output.get("repairRunId")).asString().isNotBlank();

    Map<String, Object> article = getJson("/api/articles/" + articleId);
    String contentText = String.valueOf(article.get("content"));
    assertThat(contentText).doesNotContain("->");

    Map<String, Object> afterAudit = postJson("/api/articles/" + articleId + "/audit");
    int afterScore = ((Number) afterAudit.get("score")).intValue();
    assertThat(afterScore >= beforeScore || !contentText.contains("->")).isTrue();
  }
}
