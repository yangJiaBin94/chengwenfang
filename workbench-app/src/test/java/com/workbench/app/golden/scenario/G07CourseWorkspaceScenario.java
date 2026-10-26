package com.workbench.app.golden.scenario;

import cn.hutool.crypto.digest.DigestUtil;
import com.workbench.app.golden.GoldenScenario;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.driver.ApiV1ScenarioDriver;
import com.workbench.app.golden.driver.ScenarioDriver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G07：课程编辑、人工审核、版本恢复、排版与发布快照。
 */
public final class G07CourseWorkspaceScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G07";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G07 需要 ApiV1ScenarioDriver");
    }
    CourseGoldenSupport.prepareCoursePipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      Map<String, String> ids = CourseGoldenSupport.createCommittedCourseShell(api);
      String courseId = ids.get("courseId");
      context.put("courseId", courseId);

      Map<String, Object> content = api.getJson("/api/courses/" + courseId + "/content");
      String articleId = String.valueOf(content.get("articleArticleId"));
      context.put("articleId", articleId);

      // 保存两版内容，便于后续恢复到第一版
      api.putJson(
          "/api/articles/" + articleId,
          Map.of(
              "content", "# 黄金工作台\n\n第一版正文",
              "source", "manual_edit",
              "reason", "g07-v1"));
      List<Map<String, Object>> versionsAfterV1 =
          api.getJsonList("/api/articles/" + articleId + "/versions");
      String restoreVersionId = String.valueOf(versionsAfterV1.get(0).get("id"));

      api.putJson(
          "/api/articles/" + articleId,
          Map.of(
              "content", "# 黄金工作台\n\n第二版正文（将被恢复覆盖）",
              "source", "manual_edit",
              "reason", "g07-v2"));

      Map<String, Object> approved =
          api.putJson(
              "/api/articles/" + articleId + "/human-review",
              Map.of("status", "approved", "comment", "g07 ok"));
      context.put("humanReviewApproved", approved);

      api.putJson(
          "/api/articles/" + articleId + "/human-review",
          Map.of("status", "rejected", "comment", "g07 reject then clear"));
      api.deleteJson("/api/articles/" + articleId + "/human-review");
      api.putJson(
          "/api/articles/" + articleId + "/human-review",
          Map.of("status", "approved", "comment", "g07 final"));

      Map<String, Object> restored =
          api.postJson("/api/articles/" + articleId + "/restore/" + restoreVersionId, null);
      context.put("restoredContent", restored.get("content"));

      Map<String, Object> layout = api.getJson("/api/articles/" + articleId + "/layout");
      Map<String, Object> savedLayout =
          api.putJson(
              "/api/articles/" + articleId + "/layout",
              Map.of(
                  "articleVersionNo", layout.get("articleVersionNo"),
                  "themeVersionId", "theme-v1",
                  "options", Map.of("fontSize", 16)));
      context.put("layout", savedLayout);

      Map<String, Object> snapshot =
          api.postJson(
              "/api/articles/" + articleId + "/publishing-snapshots",
              Map.of(
                  "articleVersionNo", savedLayout.get("articleVersionNo"),
                  "themeVersionId", "theme-v1",
                  "options", Map.of()));
      context.put("snapshot", snapshot);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G07 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String articleId = context.get("articleId");
    try {
      Map<String, Object> article = api.getJson("/api/articles/" + articleId);
      List<Map<String, Object>> versions = api.getJsonList("/api/articles/" + articleId + "/versions");
      Map<String, Object> humanReview = api.getJson("/api/articles/" + articleId + "/human-review");
      Map<String, Object> layout = api.getJson("/api/articles/" + articleId + "/layout");
      List<Map<String, Object>> snapshots =
          api.getJsonList("/api/articles/" + articleId + "/publishing-snapshots");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("courseId", context.get("courseId"));
      outputs.put("articleId", articleId);
      outputs.put("content", article.get("content"));
      outputs.put("restoredContent", context.get("restoredContent"));
      outputs.put("humanReview", slimHumanReview(humanReview));
      outputs.put("layout", slimLayout(layout));
      outputs.put("versions", slimVersions(versions));
      outputs.put("snapshots", slimSnapshots(snapshots));
      // 工作区场景无主任务；导出空事件流，仍走统一 pack 结构
      return api.packArtifacts("", outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G07 采集失败", exception);
    }
  }

  private static Map<String, Object> slimHumanReview(Map<String, Object> review) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (review == null) {
      return out;
    }
    out.put("status", review.get("status"));
    out.put("comment", review.get("comment"));
    return out;
  }

  private static Map<String, Object> slimLayout(Map<String, Object> layout) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (layout == null) {
      return out;
    }
    out.put("themeVersionId", layout.get("themeVersionId"));
    out.put("options", layout.get("options"));
    out.put("articleVersionNo", layout.get("articleVersionNo"));
    return out;
  }

  private static List<Map<String, Object>> slimVersions(List<Map<String, Object>> versions) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (versions == null) {
      return out;
    }
    for (Map<String, Object> version : versions) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("versionNo", version.get("versionNo"));
      row.put("source", version.get("source"));
      row.put("reason", version.get("reason"));
      out.add(row);
    }
    return out;
  }

  private static List<Map<String, Object>> slimSnapshots(List<Map<String, Object>> snapshots) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (snapshots == null) {
      return out;
    }
    for (Map<String, Object> snapshot : snapshots) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("articleVersionNo", snapshot.get("articleVersionNo"));
      row.put("themeVersionId", snapshot.get("themeVersionId"));
      String html = snapshot.get("html") == null ? "" : String.valueOf(snapshot.get("html"));
      row.put("htmlSha256", DigestUtil.sha256Hex(html));
      row.put("htmlLength", html.length());
      out.add(row);
    }
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
