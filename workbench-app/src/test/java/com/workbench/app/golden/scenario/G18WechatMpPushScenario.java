package com.workbench.app.golden.scenario;

import com.workbench.app.golden.GoldenScenario;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.driver.ApiV1ScenarioDriver;
import com.workbench.app.golden.driver.ScenarioDriver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * G18：公众号配置、连通性测试与推送草稿（外部 API 桩）。
 */
public final class G18WechatMpPushScenario implements GoldenScenario {
  /** 场景 ID。 */
  public static final String ID = "G18";

  /** 1×1 PNG data URL，满足封面图硬性要求。 */
  private static final String COVER_DATA_URL =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  private static final String PUSH_MARKDOWN =
      """
      # 黄金睡眠推送稿

      ![](%s)

      ## 背景
      这是一段用于公众号草稿推送的基线正文，包含可识别结构。

      ## 行动
      读者可按步骤检查睡眠节律边界。
      """.formatted(COVER_DATA_URL);

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void prepare(ScenarioDriver driver, GoldenScenarioContext context) {
    if (!(driver instanceof ApiV1ScenarioDriver api)) {
      throw new IllegalStateException("G18 需要 ApiV1ScenarioDriver");
    }
    HotNewsGoldenSupport.prepareHotNewsPipeline(api, context);
  }

  @Override
  public void execute(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      Map<String, Object> settings =
          api.putJson(
              "/api/settings/wechat-mp",
              Map.of(
                  "accountName", "黄金基线公众号",
                  "appId", "wx_golden_app",
                  "appSecret", "golden_secret",
                  "defaultAuthor", "基线作者"));
      context.put("settingsAfterSave", settings);

      Map<String, Object> test =
          api.postJson(
              "/api/settings/wechat-mp/test",
              Map.of("appId", "wx_golden_app", "appSecret", "golden_secret"));
      context.put("connectionTest", test);

      String themeId =
          HotNewsGoldenSupport.prepareThemeWithRadarAndEditorial(
              api, "g18_golden_theme", "黄金睡眠热点");
      context.put("themeId", themeId);
      String topicId = HotNewsGoldenSupport.firstRadarTopicId(api, themeId);
      Map<String, Object> articleShell =
          api.postJson("/api/hot-news/topics/" + topicId + "/ensure-article", null);
      String articleId = String.valueOf(articleShell.get("id"));
      context.put("articleId", articleId);
      api.putJson(
          "/api/hot-news/articles/" + articleId,
          Map.of("title", "黄金睡眠推送稿", "content", PUSH_MARKDOWN, "draftContent", PUSH_MARKDOWN));

      List<Map<String, Object>> themes = api.getJsonList("/api/layout/themes");
      String themeVersionId = firstThemeVersionId(themes);

      Map<String, Object> pushBody = new LinkedHashMap<>();
      pushBody.put("articleId", articleId);
      pushBody.put("markdown", PUSH_MARKDOWN);
      pushBody.put("themeVersionId", themeVersionId);
      pushBody.put("title", "黄金睡眠推送稿");
      pushBody.put("author", "基线作者");
      pushBody.put("coverSrc", COVER_DATA_URL);
      Map<String, Object> push = api.postJson("/api/publishing/wechat-mp/draft", pushBody);
      context.put("pushResult", push);
    } catch (RuntimeException runtime) {
      throw runtime;
    } catch (Exception exception) {
      throw new IllegalStateException("G18 执行失败", exception);
    }
  }

  @Override
  public Map<String, String> collect(ScenarioDriver driver, GoldenScenarioContext context) {
    ApiV1ScenarioDriver api = (ApiV1ScenarioDriver) driver;
    try {
      Map<String, Object> settings = api.getJson("/api/settings/wechat-mp");
      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put("themeId", context.get("themeId"));
      outputs.put("articleId", context.get("articleId"));
      outputs.put("connectionTest", context.get("connectionTest"));
      outputs.put("pushResult", slimPush(context.get("pushResult")));
      outputs.put("settings", slimSettings(settings));
      return api.packArtifacts("", outputs);
    } catch (Exception exception) {
      throw new IllegalStateException("G18 采集失败", exception);
    }
  }

  private static String firstThemeVersionId(List<Map<String, Object>> themes) {
    if (themes != null) {
      for (Map<String, Object> theme : themes) {
        Object versionId = theme.get("currentVersionId");
        if (versionId != null && !String.valueOf(versionId).isBlank()) {
          return String.valueOf(versionId);
        }
      }
    }
    return "";
  }

  private static Map<String, Object> slimPush(Object push) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!(push instanceof Map<?, ?> map)) {
      return out;
    }
    out.put("mediaId", map.get("mediaId"));
    out.put("title", map.get("title"));
    out.put("uploadedImageCount", map.get("uploadedImageCount"));
    out.put("updatedExisting", map.get("updatedExisting"));
    return out;
  }

  private static Map<String, Object> slimSettings(Map<String, Object> settings) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (settings == null) {
      return out;
    }
    out.put("accountName", settings.get("accountName"));
    out.put("appId", settings.get("appId"));
    out.put("appSecretConfigured", settings.get("appSecretConfigured"));
    out.put("defaultAuthor", settings.get("defaultAuthor"));
    out.put("lastDraftMediaId", settings.get("lastDraftMediaId"));
    out.put("lastPushedAt", settings.get("lastPushedAt"));
    return out;
  }

  @Override
  public String toString() {
    return id();
  }
}
