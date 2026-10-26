package com.chengwenfang.app.golden.scenario;

import com.chengwenfang.app.golden.GoldenScenario;
import com.chengwenfang.app.golden.GoldenScenarioContext;
import com.chengwenfang.app.golden.driver.ApiV1ScenarioDriver;
import com.chengwenfang.app.golden.driver.ScenarioDriver;
import com.chengwenfang.job.facade.constant.JobTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G03：课程原稿单步生成。
 */
public final class G03CourseDraftGenerationScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G03";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G03 需要 ApiV1ScenarioDriver");
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
      Map<String, Object> started =
          api.postJson(
              "/api/courses/" + courseId + "/generate",
              Map.of("type", JobTypes.COURSE_DRAFT_GENERATION));
      String jobId = ApiV1ScenarioDriver.extractJobId(started);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 120);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("原稿生成失败: " + terminal);
      }
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G03 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String courseId = context.get("courseId");
    String jobId = context.get("jobId");
    try {
      Map<String, Object> content = api.getJson("/api/courses/" + courseId + "/content");
      String draftArticleId = String.valueOf(content.get("draftArticleId"));
      List<Map<String, Object>> versions =
          api.getJsonList("/api/articles/" + draftArticleId + "/versions");
      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("courseId", courseId);
      outputs.put("draft", content.get("draft"));
      outputs.put("versions", versions);
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G03 采集失败", exception);
    }
  }

  @Override
  public String toString() {
    return id();
  }
}
