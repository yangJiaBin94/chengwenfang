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
 * G05：单张配图生成。
 */
public final class G05ImageGenerationScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G05";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G05 需要 ApiV1ScenarioDriver");
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

      // 先生成原稿，确保公众号文档壳存在
      Map<String, Object> draftStarted =
          api.postJson(
              "/api/courses/" + courseId + "/generate",
              Map.of("type", JobTypes.COURSE_DRAFT_GENERATION));
      api.awaitTerminal(ApiV1ScenarioDriver.extractJobId(draftStarted), 120);

      Map<String, Object> content = api.getJson("/api/courses/" + courseId + "/content");
      String articleId = String.valueOf(content.get("articleArticleId"));
      context.put("articleId", articleId);

      Map<String, Object> started =
          api.postJson(
              "/api/articles/" + articleId + "/images",
              Map.of(
                  "prompt", "golden cover diagram for lesson",
                  "size", "1024x1024",
                  "kind", "cover",
                  "sequenceNo", 1));
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 90);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("配图生成失败: " + terminal);
      }
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G05 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String articleId = context.get("articleId");
    String jobId = context.get("jobId");
    try {
      List<Map<String, Object>> images = api.getJsonList("/api/articles/" + articleId + "/images");
      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("articleId", articleId);
      outputs.put("images", CourseGoldenSupport.slimImages(images));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G05 采集失败", exception);
    }
  }

  @Override
  public String toString() {
    return id();
  }
}
