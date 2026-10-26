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
 * G06：课程审核与修复（审核 → AI 修复 → 再审核）。
 */
public final class G06CourseArticleRepairScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G06";

  /** 含禁用箭头的待修复正文。 */
  private static final String DIRTY_CONTENT =
      "## 行动\n路径 -> 结果\n本课小结\n- ok\n下节课预告\n- next";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G06 需要 ApiV1ScenarioDriver");
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

      api.putJson("/api/articles/" + articleId, Map.of("content", DIRTY_CONTENT));

      Map<String, Object> beforeAudit = api.postJson("/api/articles/" + articleId + "/audit", null);
      context.put("beforeAudit", beforeAudit);

      Map<String, Object> started = api.postJson("/api/articles/" + articleId + "/repair", null);
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 120);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("文章修复失败: " + terminal);
      }
      context.put("job", terminal);

      Map<String, Object> afterAudit = api.postJson("/api/articles/" + articleId + "/audit", null);
      context.put("afterAudit", afterAudit);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G06 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String articleId = context.get("articleId");
    String jobId = context.get("jobId");
    try {
      Map<String, Object> article = api.getJson("/api/articles/" + articleId);
      List<Map<String, Object>> versions = api.getJsonList("/api/articles/" + articleId + "/versions");
      Map<String, Object> job = context.get("job");

      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("courseId", context.get("courseId"));
      outputs.put("articleId", articleId);
      outputs.put("jobId", jobId);
      outputs.put("content", article.get("content"));
      outputs.put("beforeAudit", slimAudit(context.get("beforeAudit")));
      outputs.put("afterAudit", slimAudit(context.get("afterAudit")));
      outputs.put("repairOutput", slimRepairOutput(job == null ? null : castMap(job.get("output"))));
      outputs.put("versions", slimVersions(versions));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G06 采集失败", exception);
    }
  }

  /**
   * 精简审核结果，去掉易变 ID / 时间，保留分数与问题码。
   *
   * @param audit 原始审核
   * @return 稳定视图
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> slimAudit(Object audit) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!(audit instanceof Map<?, ?> raw)) {
      return out;
    }
    Map<String, Object> map = (Map<String, Object>) raw;
    out.put("score", map.get("score"));
    out.put("passed", map.get("passed"));
    Object issues = map.get("issues");
    if (issues instanceof List<?> list) {
      List<Map<String, Object>> slimIssues = new ArrayList<>();
      for (Object item : list) {
        if (!(item instanceof Map<?, ?> issueMap)) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", issueMap.get("code"));
        row.put("severity", issueMap.get("severity"));
        row.put("message", issueMap.get("message"));
        slimIssues.add(row);
      }
      out.put("issues", slimIssues);
    }
    return out;
  }

  /**
   * 精简修复任务输出。
   *
   * @param output Job output
   * @return 稳定视图
   */
  private static Map<String, Object> slimRepairOutput(Map<String, Object> output) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (output == null) {
      return out;
    }
    out.put("ok", output.get("ok"));
    out.put("resolvedCodes", output.get("resolvedCodes"));
    out.put("addedCodes", output.get("addedCodes"));
    out.put("beforeScore", scoreOf(output.get("beforeAudit")));
    out.put("afterScore", scoreOf(output.get("afterAudit")));
    return out;
  }

  private static Object scoreOf(Object audit) {
    if (audit instanceof Map<?, ?> map) {
      return map.get("score");
    }
    return null;
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
  private static Map<String, Object> castMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return null;
  }

  @Override
  public String toString() {
    return id();
  }
}
