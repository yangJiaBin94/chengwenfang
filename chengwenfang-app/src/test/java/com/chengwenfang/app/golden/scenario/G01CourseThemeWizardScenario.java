package com.chengwenfang.app.golden.scenario;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.app.golden.GoldenScenario;
import com.chengwenfang.app.golden.GoldenScenarioContext;
import com.chengwenfang.app.golden.driver.ApiV1ScenarioDriver;
import com.chengwenfang.app.golden.driver.ScenarioDriver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * G01：课程主题向导与课表生成。
 */
public final class G01CourseThemeWizardScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G01";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    driver.reset(context);
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G01 需要 ApiV1ScenarioDriver");
    }
    try {
      api.pointAiProviders();
    } catch (Exception exception) {
      throw new IllegalStateException("指向 AI 桩失败", exception);
    }
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      Map<String, Object> draft =
          api.postJson(
              "/api/modes/course_system/themes/drafts",
              Map.of(
                  "name",
                  "黄金课表主题",
                  "description",
                  "G01 课表生成基线",
                  "code",
                  "g01_golden_theme"));
      String themeId = String.valueOf(draft.get("id"));
      context.put("themeId", themeId);

      Map<String, Object> briefingPatch = new LinkedHashMap<>();
      briefingPatch.put("name", "黄金课表主题");
      briefingPatch.put("description", "G01 课表生成基线");
      briefingPatch.put("briefing", CourseGoldenSupport.sampleBriefing("黄金课表主题", "G01 课表生成基线"));
      api.patchJson("/api/themes/" + themeId + "/briefing", briefingPatch);

      Map<String, Object> generate = api.postJson("/api/themes/" + themeId + "/curriculum:generate", null);
      String jobId = ApiV1ScenarioDriver.extractJobId(generate);
      context.put("jobId", jobId);
      Map<String, Object> terminal = api.awaitTerminal(jobId, 120);
      if (!"succeeded".equals(String.valueOf(terminal.get("status")))) {
        throw new IllegalStateException("课表生成失败: " + terminal);
      }

      Map<String, Object> curriculumDraft = api.getJson("/api/themes/" + themeId + "/curriculum-draft");
      context.put("curriculumDraft", curriculumDraft);
      api.putJson("/api/themes/" + themeId + "/curriculum-draft", Map.of("draft", curriculumDraft));

      Map<String, Object> commit = api.postJson("/api/themes/" + themeId + "/curriculum:commit", null);
      context.put("commit", commit);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G01 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    String themeId = context.get("themeId");
    String jobId = context.get("jobId");
    try {
      Map<String, Object> theme = api.getJson("/api/themes/" + themeId);
      Map<String, Object> route = api.getJson("/api/themes/" + themeId + "/route");
      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("theme", stabilizeTheme(theme));
      outputs.put("briefingJson", canonicalizeJsonText(theme.get("briefingJson")));
      outputs.put("defaultsJson", canonicalizeJsonText(theme.get("defaultsJson")));
      outputs.put("curriculumDraft", context.get("curriculumDraft"));
      outputs.put("route", route);
      outputs.put("commit", context.get("commit"));
      return api.packArtifacts(api.exportJobEventsJsonl(jobId), outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G01 采集失败", exception);
    }
  }

  /**
   * 去掉易变计数字段，保留语义字段（ID 仍交 Normalizer 处理）。
   *
   * @param theme 主题视图
   * @return 稳定副本
   */
  private static Map<String, Object> stabilizeTheme(Map<String, Object> theme) {
    Map<String, Object> copy = new LinkedHashMap<>(theme);
    copy.remove("briefingJson");
    copy.remove("defaultsJson");
    return copy;
  }

  /**
   * 将 JSON 字符串按键排序后重序列化，消除 Map 键序抖动。
   *
   * @param raw 原始值
   * @return 规范串；非 JSON 原样
   */
  private static Object canonicalizeJsonText(Object raw) {
    if (raw == null) {
      return null;
    }
    String text = StrUtil.trim(String.valueOf(raw));
    if (!JSONUtil.isTypeJSON(text)) {
      return text;
    }
    Object parsed = JSONUtil.parse(text);
    return JSONUtil.toJsonStr(sortKeys(parsed));
  }

  private static Object sortKeys(Object node) {
    if (node instanceof JSONObject object) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      for (String key : object.keySet()) {
        sorted.put(key, sortKeys(object.get(key)));
      }
      return sorted;
    }
    if (node instanceof Iterable<?> iterable) {
      java.util.ArrayList<Object> list = new java.util.ArrayList<>();
      for (Object element : iterable) {
        list.add(sortKeys(element));
      }
      return list;
    }
    if (node instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        sorted.put(String.valueOf(entry.getKey()), sortKeys(entry.getValue()));
      }
      return sorted;
    }
    return node;
  }

  @Override
  public String toString() {
    return id();
  }
}
