package com.chengwenfang.app.golden.scenario;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.app.golden.GoldenScenarioContext;
import com.chengwenfang.app.golden.driver.ApiV1ScenarioDriver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 课程黄金场景共用：指桩、发布课表工作流、创建已确认课壳。
 */
public final class CourseGoldenSupport {
  private CourseGoldenSupport() {}

  /**
   * 重置后指向 AI 桩，并确保课表生成工作流已发布。
   *
   * @param api     驱动
   * @param context 上下文
   */
  public static void prepareCoursePipeline(ApiV1ScenarioDriver api, GoldenScenarioContext context) {
    api.reset(context);
    try {
      api.pointAiProviders();
      ensurePublishedCourseWorkflow(api);
    } catch (Exception exception) {
      throw new IllegalStateException("课程场景准备失败", exception);
    }
  }

  /**
   * 走主题向导 → 课表生成 → 确认，返回第一门课 ID。
   *
   * @param api 驱动
   * @return themeId / courseId
   */
  public static Map<String, String> createCommittedCourseShell(ApiV1ScenarioDriver api) {
    try {
      String code = "g_course_" + UUID.randomUUID().toString().substring(0, 8);
      Map<String, Object> draft =
          api.postJson(
              "/api/modes/course_system/themes/drafts",
              Map.of(
                  "name", "黄金课程主题",
                  "description", "课程场景课壳",
                  "code", code));
      String themeId = String.valueOf(draft.get("id"));

      Map<String, Object> briefingPatch = new LinkedHashMap<>();
      briefingPatch.put("name", "黄金课程主题");
      briefingPatch.put("description", "课程场景课壳");
      briefingPatch.put("briefing", sampleBriefing("黄金课程主题", "课程场景课壳"));
      api.patchJson("/api/themes/" + themeId + "/briefing", briefingPatch);

      Map<String, Object> generate =
          api.postJson("/api/themes/" + themeId + "/curriculum:generate", null);
      String jobId = ApiV1ScenarioDriver.extractJobId(generate);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 120);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("课表生成失败: " + terminal);
      }

      Map<String, Object> curriculumDraft = api.getJson("/api/themes/" + themeId + "/curriculum-draft");
      api.putJson("/api/themes/" + themeId + "/curriculum-draft", Map.of("draft", curriculumDraft));
      api.postJson("/api/themes/" + themeId + "/curriculum:commit", null);

      Map<String, Object> route = api.getJson("/api/themes/" + themeId + "/route");
      String courseId = firstCourseId(route);
      Map<String, String> ids = new LinkedHashMap<>();
      ids.put("themeId", themeId);
      ids.put("courseId", courseId);
      return ids;
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("创建课壳失败", exception);
    }
  }

  /**
   * 确保 {@code course-content-generation} 已发布为「目录全图 + 空 Skill 绑定」。
   * <p>必须每次强制发布：host 库可能被其它 IT（如 FormalCourseGenerationIT）覆盖为带 Skill / 缺
   * {@code snapshot_context} 的图，导致 G02 与基线偏离。
   *
   * @param api 驱动
   * @throws Exception HTTP 失败
   */
  public static void ensurePublishedCourseWorkflow(ApiV1ScenarioDriver api) throws Exception {
    List<Map<String, Object>> workflows = api.getJsonList("/api/workflows");
    String workflowId = null;
    for (Map<String, Object> workflow : workflows) {
      if ("course-content-generation".equals(String.valueOf(workflow.get("key")))) {
        workflowId = String.valueOf(workflow.get("id"));
        break;
      }
    }
    if (workflowId == null) {
      Map<String, Object> created =
          api.postJson(
              "/api/workflows",
              Map.of(
                  "name", "Course content generation",
                  "key", "course-content-generation",
                  "scene", "course_content_generation"));
      workflowId = String.valueOf(created.get("id"));
    }
    Map<String, Object> draft = api.getJson("/api/workflows/" + workflowId + "/draft");
    // 用内置目录全图（含 snapshot_context），但清空 skillBindings，对齐黄金基线无 Skill 注入
    Map<String, Object> definition = catalogCourseDefinitionWithoutSkills();
    api.putJson(
        "/api/workflows/" + workflowId + "/draft",
        Map.of("revision", draft.get("revision"), "definition", definition));
    api.postJson(
        "/api/workflows/" + workflowId + "/publish",
        Map.of("version", "golden-" + UUID.randomUUID().toString().substring(0, 8)));
  }

  /**
   * 从 {@code builtin-catalog.json} 读取课表生成图并清空全部节点 Skill 绑定。
   *
   * @return 定义 Map；找不到目录时回退精简图
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> catalogCourseDefinitionWithoutSkills() {
    Path catalog = resolveBuiltinCatalog();
    if (catalog == null) {
      return minimalCourseDefinition();
    }
    try {
      String raw = Files.readString(catalog, StandardCharsets.UTF_8);
      JSONObject root = JSONUtil.parseObj(raw);
      JSONArray workflows = root.getJSONArray("workflows");
      if (workflows == null) {
        return minimalCourseDefinition();
      }
      for (int index = 0; index < workflows.size(); index++) {
        JSONObject workflow = workflows.getJSONObject(index);
        if (workflow == null || !"course-content-generation".equals(workflow.getStr("key"))) {
          continue;
        }
        JSONObject definition = workflow.getJSONObject("definition");
        if (definition == null) {
          break;
        }
        JSONObject copy = JSONUtil.parseObj(definition.toString());
        JSONArray nodes = copy.getJSONArray("nodes");
        if (nodes != null) {
          for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            JSONObject node = nodes.getJSONObject(nodeIndex);
            if (node != null) {
              node.set("skillBindings", new JSONArray());
            }
          }
        }
        return copy.toBean(Map.class);
      }
    } catch (Exception ignored) {
      // 回退精简图
    }
    return minimalCourseDefinition();
  }

  /**
   * 定位仓库内置目录 JSON。
   *
   * @return 文件路径；找不到则 null
   */
  private static Path resolveBuiltinCatalog() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    Path[] candidates = {
      cwd.resolve("scripts/bootstrap/builtin-catalog.json"),
      cwd.resolve("../scripts/bootstrap/builtin-catalog.json"),
      cwd.resolve("../../scripts/bootstrap/builtin-catalog.json")
    };
    for (Path candidate : candidates) {
      if (Files.isRegularFile(candidate)) {
        return candidate.normalize();
      }
    }
    return null;
  }

  /**
   * 从 route 视图取第一门课 ID。
   *
   * @param route 路线
   * @return courseId
   */
  public static String firstCourseId(Map<String, Object> route) {
    Object seriesRaw = route.get("series");
    if (!(seriesRaw instanceof List<?> seriesList) || seriesList.isEmpty()) {
      throw new IllegalStateException("route 无 series: " + route);
    }
    Object firstSeries = seriesList.get(0);
    if (!(firstSeries instanceof Map<?, ?> seriesMap)) {
      throw new IllegalStateException("series[0] 非法");
    }
    Object lessonsRaw = seriesMap.get("lessons");
    if (!(lessonsRaw instanceof List<?> lessons) || lessons.isEmpty()) {
      throw new IllegalStateException("series 无 lessons");
    }
    Object firstLesson = lessons.get(0);
    if (!(firstLesson instanceof Map<?, ?> lessonMap)) {
      throw new IllegalStateException("lesson[0] 非法");
    }
    Object courseId = lessonMap.get("id");
    if (courseId == null) {
      throw new IllegalStateException("lesson 无 id");
    }
    return String.valueOf(courseId);
  }

  /**
   * 精简节点运行列表，只保留稳定字段。
   *
   * @param nodeRuns 原始列表
   * @return 稳定列表
   */
  public static List<Map<String, Object>> slimNodeRuns(List<Map<String, Object>> nodeRuns) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (nodeRuns == null) {
      return out;
    }
    for (Map<String, Object> nodeRun : nodeRuns) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("nodeKey", nodeRun.get("nodeKey"));
      row.put("sequenceNo", nodeRun.get("sequenceNo"));
      row.put("status", nodeRun.get("status"));
      row.put("typeKey", nodeRun.get("typeKey"));
      out.add(row);
    }
    return out;
  }

  /**
   * 精简配图列表。
   *
   * @param images 原始
   * @return 稳定列表
   */
  public static List<Map<String, Object>> slimImages(List<Map<String, Object>> images) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (images == null) {
      return out;
    }
    for (Map<String, Object> image : images) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("kind", image.get("kind"));
      row.put("sequenceNo", image.get("sequenceNo"));
      row.put("prompt", image.get("prompt"));
      row.put("status", image.get("status"));
      out.add(row);
    }
    return out;
  }

  /**
   * 主题问卷样例（键序固定）。
   *
   * @param name        主题名
   * @param description 说明
   * @return briefing
   */
  public static Map<String, Object> sampleBriefing(String name, String description) {
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("schema_version", 1);
    briefing.put("step", 7);
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", name);
    naming.put("description", description);
    naming.put("domain", "ai_intro");
    naming.put("domain_custom", "");
    briefing.put("naming", naming);
    Map<String, Object> audience = new LinkedHashMap<>();
    audience.put("readers", List.of("zero_basis"));
    audience.put("readers_note", "");
    audience.put("prerequisites", List.of("none"));
    audience.put("time_budget", "system_1_2m");
    briefing.put("audience", audience);
    Map<String, Object> goals = new LinkedHashMap<>();
    goals.put("outcomes", List.of("能复述核心概念"));
    goals.put("success_criteria", "understand");
    briefing.put("goals", goals);
    Map<String, Object> scope = new LinkedHashMap<>();
    scope.put("must_cover", List.of("基础概念"));
    scope.put("out_of_scope", List.of());
    scope.put("difficulty_curve", "easy_to_hard");
    briefing.put("scope", scope);
    Map<String, Object> structure = new LinkedHashMap<>();
    structure.put("series_count_pref", "3_5");
    structure.put("series_count_custom", null);
    structure.put("lessons_per_series_pref", "3_5");
    structure.put("lessons_per_series_custom", null);
    structure.put("need_overview_lessons", true);
    structure.put("lesson_forms", List.of("concept", "practice"));
    briefing.put("structure", structure);
    Map<String, Object> expression = new LinkedHashMap<>();
    expression.put("tone", "companion");
    expression.put("length", "standard");
    expression.put("boundaries", "");
    briefing.put("expression", expression);
    return briefing;
  }

  private static Map<String, Object> minimalCourseDefinition() {
    List<Map<String, Object>> nodes = new ArrayList<>();
    nodes.add(node("n_start", "start", "Start", 0));
    nodes.add(node("n_open", "draft_opening", "Opening", 1));
    nodes.add(node("n_core", "draft_core", "Core", 2));
    nodes.add(node("n_ex", "draft_example", "Example", 3));
    nodes.add(node("n_sum", "draft_summary", "Summary", 4));
    nodes.add(node("n_prev", "draft_preview", "Preview", 5));
    nodes.add(node("n_wechat", "wechat_conversion", "Wechat", 6));
    nodes.add(node("n_cover", "image_cover", "Cover", 7));
    nodes.add(node("n_end", "end", "End", 8));
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("schemaVersion", "1");
    definition.put("name", "Course content generation");
    definition.put("description", "");
    definition.put("scene", "course_content_generation");
    definition.put("modeId", "course-system");
    definition.put("nodes", nodes);
    definition.put(
        "edges",
        List.of(
            edge("n_start", "n_open"),
            edge("n_open", "n_core"),
            edge("n_core", "n_ex"),
            edge("n_ex", "n_sum"),
            edge("n_sum", "n_prev"),
            edge("n_prev", "n_wechat"),
            edge("n_wechat", "n_cover"),
            edge("n_cover", "n_end")));
    return definition;
  }

  private static Map<String, Object> node(String key, String typeKey, String name, int sequenceNo) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("key", key);
    node.put("typeKey", typeKey);
    node.put("name", name);
    node.put("enabled", true);
    node.put("sequenceNo", sequenceNo);
    node.put("config", Map.of());
    node.put("skillBindings", List.of());
    return node;
  }

  private static Map<String, Object> edge(String source, String target) {
    Map<String, Object> edge = new LinkedHashMap<>();
    edge.put("id", source + "-" + target);
    edge.put("source", source);
    edge.put("target", target);
    edge.put("loop", false);
    return edge;
  }
}
