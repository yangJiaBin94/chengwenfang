package com.workbench.app.golden.scenario;

import com.workbench.app.golden.GoldenScenario;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.driver.ApiV1ScenarioDriver;
import com.workbench.app.golden.driver.ScenarioDriver;
import com.workbench.job.facade.constant.JobTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G04：公众号单步生成（先原稿后公众号）。
 */
public final class G04WechatArticleGenerationScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G04";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G04 需要 ApiV1ScenarioDriver");
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

      Map<String, Object> draftStarted =
          api.postJson(
              "/api/courses/" + courseId + "/generate",
              Map.of("type", JobTypes.COURSE_DRAFT_GENERATION));
      String draftJobId = ApiV1ScenarioDriver.extractJobId(draftStarted);
      Map<String, Object> draftTerminal = api.awaitTerminal(draftJobId, 120);
      if (!"succeeded".equals(String.valueOf(draftTerminal.get("status")))) {
        throw new IllegalStateException("原稿前置失败: " + draftTerminal);
      }

      Map<String, Object> wechatStarted =
          api.postJson(
              "/api/courses/" + courseId + "/generate",
              Map.of("type", JobTypes.WECHAT_ARTICLE_GENERATION));
      String jobId = ApiV1ScenarioDriver.extractJobId(wechatStarted);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 120);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("公众号生成失败: " + terminal);
      }
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G04 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String courseId = context.get("courseId");
    String jobId = context.get("jobId");
    try {
      Map<String, Object> content = api.getJson("/api/courses/" + courseId + "/content");
      String articleArticleId = String.valueOf(content.get("articleArticleId"));
      List<Map<String, Object>> versions =
          api.getJsonList("/api/articles/" + articleArticleId + "/versions");
      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("courseId", courseId);
      outputs.put("article", content.get("article"));
      outputs.put("versions", versions);
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G04 采集失败", exception);
    }
  }

  @Override
  public String toString() {
    return id();
  }
}
