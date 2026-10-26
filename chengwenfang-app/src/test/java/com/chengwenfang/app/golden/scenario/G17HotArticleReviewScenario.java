package com.chengwenfang.app.golden.scenario;

import com.chengwenfang.app.golden.GoldenScenario;
import com.chengwenfang.app.golden.GoldenScenarioContext;
import com.chengwenfang.app.golden.driver.ApiV1ScenarioDriver;
import com.chengwenfang.app.golden.driver.ScenarioDriver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G17：热点审稿全流程（审核 → 单条修复拒绝 → 全部修复接受 → 复检 → 人工裁定）。
 */
public final class G17HotArticleReviewScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G17";

  /** 触发阻断规则的脏正文。 */
  private static final String DIRTY_CONTENT = "## 重复\n\n短文\n\n## 重复";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G17 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      String themeId =
          HotNewsGoldenSupport.prepareThemeWithRadarAndEditorial(
              api, "g17_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      String topicId = HotNewsGoldenSupport.firstRadarTopicId(api, themeId);
      context.put("topicId", topicId);

      api.postJson("/api/hot-news/topics/" + topicId + "/article-skeleton", null);
      Map<String, Object> brief =
          api.postJson("/api/hot-news/topics/" + topicId + "/briefs", null);
      String briefId = String.valueOf(brief.get("id"));
      api.postJson(
          "/api/hot-news/briefs/" + briefId + "/confirm",
          Map.of("note", "黄金基线确认简报后审稿"));

      Map<String, Object> generated =
          api.postJson("/api/hot-news/briefs/" + briefId + "/generate-article", null);
      String generateJobId = ApiV1ScenarioDriver.extractJobId(generated);
      String articleId = String.valueOf(generated.get("entityId"));
      context.put("articleId", articleId);
      Map<String, Object> generateTerminal = api.awaitTerminal(generateJobId, 300);
      if (!"succeeded".equals(String.valueOf(generateTerminal.get("status")))) {
        throw new IllegalStateException("热点成文失败: " + generateTerminal);
      }

      Map<String, Object> reviewStarted =
          api.postJson(
              "/api/hot-article-reviews",
              Map.of(
                  "articleId", articleId,
                  "content", DIRTY_CONTENT,
                  "contentHash", "g17-bad-hash",
                  "sources", List.of(),
                  "mode", "full"));
      String reviewId = ApiV1ScenarioDriver.extractJobId(reviewStarted);
      context.put("reviewId", reviewId);
      Map<String, Object> reviewTerminal = api.awaitTerminal(reviewId, 120);
      if (!"succeeded".equals(String.valueOf(reviewTerminal.get("status")))) {
        throw new IllegalStateException("热点审稿失败: " + reviewTerminal);
      }
      context.put("reviewJob", reviewTerminal);

      Map<String, Object> latest =
          api.getJson("/api/articles/" + articleId + "/hot-reviews/latest");
      context.put("latestReview", latest);
      List<String> issueIds = firstIssueIds(latest);
      if (issueIds.isEmpty()) {
        throw new IllegalStateException("审稿无问题条目: " + latest);
      }

      // 单条修复 → 拒绝
      Map<String, Object> singleRepair =
          api.postJson("/api/hot-article-reviews/" + reviewId + "/repair", issueIds);
      String singleRepairJobId = ApiV1ScenarioDriver.extractJobId(singleRepair);
      Map<String, Object> singleRepairTerminal = api.awaitTerminal(singleRepairJobId, 120);
      if (!"succeeded".equals(String.valueOf(singleRepairTerminal.get("status")))) {
        throw new IllegalStateException("单条修复失败: " + singleRepairTerminal);
      }
      String rejectedCandidateId = String.valueOf(asMap(singleRepairTerminal.get("output")).get("candidateId"));
      context.put("rejectedCandidateId", rejectedCandidateId);
      api.postJson("/api/hot-article-repairs/" + rejectedCandidateId + "/reject", null);

      // 全部修复 → 接受 → 复检
      Map<String, Object> repairAll =
          api.postJson("/api/hot-article-reviews/" + reviewId + "/repair-all", null);
      String repairAllJobId = ApiV1ScenarioDriver.extractJobId(repairAll);
      context.put("repairAllJobId", repairAllJobId);
      Map<String, Object> repairAllTerminal = api.awaitTerminal(repairAllJobId, 120);
      if (!"succeeded".equals(String.valueOf(repairAllTerminal.get("status")))) {
        throw new IllegalStateException("全部修复失败: " + repairAllTerminal);
      }
      String acceptedCandidateId =
          String.valueOf(asMap(repairAllTerminal.get("output")).get("candidateId"));
      context.put("acceptedCandidateId", acceptedCandidateId);

      Map<String, Object> accepted =
          api.postJson("/api/hot-article-repairs/" + acceptedCandidateId + "/accept", null);
      String revalidateJobId = ApiV1ScenarioDriver.extractJobId(accepted);
      context.put("revalidateJobId", revalidateJobId);
      Map<String, Object> revalidateTerminal = api.awaitTerminal(revalidateJobId, 120);
      if (!"succeeded".equals(String.valueOf(revalidateTerminal.get("status")))) {
        throw new IllegalStateException("复检失败: " + revalidateTerminal);
      }
      context.put("revalidateJob", revalidateTerminal);

      Map<String, Object> decision =
          api.postJson(
              "/api/hot-article-reviews/" + reviewId + "/decision",
              Map.of("status", "approved", "comment", "黄金基线人工通过"));
      context.put("decision", decision);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G17 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String articleId = context.get("articleId");
    String reviewId = context.get("reviewId");
    try {
      Map<String, Object> latest =
          api.getJson("/api/articles/" + articleId + "/hot-reviews/latest");
      Map<String, Object> rejected =
          api.getJson("/api/hot-article-repairs/" + context.get("rejectedCandidateId"));
      Map<String, Object> accepted =
          api.getJson("/api/hot-article-repairs/" + context.get("acceptedCandidateId"));
      Map<String, Object> article = api.getJson("/api/hot-news/articles/" + articleId);
      List<Map<String, Object>> versions =
          api.getJsonList("/api/hot-news/articles/" + articleId + "/versions");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", context.get("themeId"));
      outputs.put("topicId", context.get("topicId"));
      outputs.put("articleId", articleId);
      outputs.put("reviewId", reviewId);
      outputs.put("latestReview", slimReview(latest));
      outputs.put("rejectedCandidate", slimCandidate(rejected));
      outputs.put("acceptedCandidate", slimCandidate(accepted));
      outputs.put("decision", context.get("decision"));
      outputs.put(
          "article",
          Map.of(
              "status", article.get("status"),
              "content", article.get("content"),
              "currentVersionNo", article.get("currentVersionNo")));
      outputs.put("versions", slimVersions(versions));
      String events =
          api.exportJobEventsJsonl(reviewId)
              + api.exportJobEventsJsonl(context.get("repairAllJobId"))
              + api.exportJobEventsJsonl(context.get("revalidateJobId"));
      return api.packArtifacts(events, outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G17 采集失败", exception);
    }
  }

  private static List<String> firstIssueIds(Map<String, Object> latest) {
    List<String> out = new ArrayList<>();
    Object items = latest == null ? null : latest.get("items");
    if (!(items instanceof List<?> list) || list.isEmpty()) {
      return out;
    }
    // 固定选用 SOURCE_DUPLICATE（脏稿含重复标题），避免并行规则产出顺序抖动
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      if ("SOURCE_DUPLICATE".equals(String.valueOf(map.get("code"))) && map.get("id") != null) {
        out.add(String.valueOf(map.get("id")));
        return out;
      }
    }
    List<Map<?, ?>> sorted = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        sorted.add(map);
      }
    }
    sorted.sort(
        (left, right) ->
            String.valueOf(left.get("code")).compareTo(String.valueOf(right.get("code"))));
    if (!sorted.isEmpty() && sorted.get(0).get("id") != null) {
      out.add(String.valueOf(sorted.get(0).get("id")));
    }
    return out;
  }

  private static Map<String, Object> slimReview(Map<String, Object> review) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (review == null) {
      return out;
    }
    out.put("status", review.get("status"));
    out.put("blockingFailed", review.get("blockingFailed"));
    out.put("failedCount", review.get("failedCount"));
    out.put("decisionStatus", review.get("decisionStatus"));
    out.put("decisionComment", review.get("decisionComment"));
    Object items = review.get("items");
    if (items instanceof List<?> list) {
      List<Map<String, Object>> slimItems = new ArrayList<>();
      for (Object item : list) {
        if (!(item instanceof Map<?, ?> map)) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", map.get("code"));
        row.put("severity", map.get("severity"));
        row.put("status", map.get("status"));
        row.put("blocking", map.get("blocking"));
        row.put("repairable", map.get("repairable"));
        slimItems.add(row);
      }
      slimItems.sort(
          (left, right) ->
              String.valueOf(left.get("code")).compareTo(String.valueOf(right.get("code"))));
      out.put("items", slimItems);
    }
    return out;
  }

  private static Map<String, Object> slimCandidate(Map<String, Object> candidate) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (candidate == null) {
      return out;
    }
    out.put("status", candidate.get("status"));
    out.put("diffSummary", candidate.get("diffSummary"));
    out.put("issueIdsJson", candidate.get("issueIdsJson"));
    out.put("content", candidate.get("content"));
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of();
  }

  @Override
  public String toString() {
    return id();
  }
}
