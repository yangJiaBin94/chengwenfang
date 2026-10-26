package com.workbench.app.golden.driver;

import static org.awaitility.Awaitility.await;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.workbench.app.golden.GoldenScenarioContext;
import com.workbench.app.golden.record.AiRecorder;
import com.workbench.app.golden.record.ExternalRecorder;
import com.workbench.app.golden.record.JobEventRecorder;
import com.workbench.app.golden.stub.ExternalSiteStubs;
import com.workbench.app.golden.stub.ScriptedAiResponder;
import com.workbench.app.support.FormalAiWireMock;
import com.workbench.crawler.facade.dto.view.FetchedItemView;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 基于现有 HTTP API 的场景驱动：清库、指桩、调用接口、等待任务、读事件与产物。
 * <p>具体 G01–G20 步骤由各 {@code GoldenScenario} 编排本驱动方法。
 */
public final class ApiV1ScenarioDriver implements ScenarioDriver {
  private final int port;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbc;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private WireMockServer aiServer;
  private WireMockServer externalServer;
  private HotNewsFacade hotNews;
  private final AiRecorder aiRecorder = new AiRecorder();
  private final ExternalRecorder externalRecorder = new ExternalRecorder();

  /**
   * @param port         本地应用端口
   * @param objectMapper JSON
   * @param jdbc         JDBC（清库）
   */
  public ApiV1ScenarioDriver(int port, ObjectMapper objectMapper, JdbcTemplate jdbc) {
    this.port = port;
    this.objectMapper = objectMapper;
    this.jdbc = jdbc;
  }

  /**
   * 绑定热点门面（用于种子待解析信源等无独立 HTTP 的 Worker 边界能力）。
   *
   * @param hotNews 热点门面；可空
   */
  public void bindHotNewsFacade(HotNewsFacade hotNews) {
    this.hotNews = hotNews;
  }

  @Override
  public String name() {
    return "api-v1";
  }

  /**
   * 绑定 AI / 外部 WireMock（由 IT 在启动后注入）。
   *
   * @param aiServer       AI 桩；可空
   * @param externalServer 外部站点桩；可空
   */
  public void bindWireMocks(WireMockServer aiServer, WireMockServer externalServer) {
    this.aiServer = aiServer;
    this.externalServer = externalServer;
    aiRecorder.bind(aiServer);
    externalRecorder.bind(externalServer);
  }

  /**
   * @return AI WireMock
   */
  public WireMockServer aiServer() {
    return aiServer;
  }

  /**
   * @return 外部 WireMock
   */
  public WireMockServer externalServer() {
    return externalServer;
  }

  /**
   * @return AI 录制器
   */
  public AiRecorder aiRecorder() {
    return aiRecorder;
  }

  /**
   * @return 外部录制器
   */
  public ExternalRecorder externalRecorder() {
    return externalRecorder;
  }

  @Override
  public void reset(GoldenScenarioContext context) {
    cleanBusinessTables();
    if (aiServer != null) {
      aiServer.resetRequests();
      ScriptedAiResponder.installStubs(aiServer);
    }
    if (externalServer != null) {
      externalServer.resetRequests();
      ExternalSiteStubs.install(externalServer);
    }
    if (context != null && context.runtimeDir() != null) {
      context.put("runtimeDir", context.runtimeDir().toString());
    }
  }

  /**
   * 将 AI / 图片供应商指向 AI WireMock。
   *
   * @throws Exception HTTP 失败
   */
  public void pointAiProviders() throws Exception {
    if (aiServer == null) {
      throw new IllegalStateException("aiServer 未绑定");
    }
    FormalAiWireMock.pointProviders(body -> putJson("/api/settings/ai", body), aiServer);
  }

  /**
   * 写入一条待解析雷达视频信源（走 {@link HotNewsFacade#persistFetchedItems}，生命周期 pending_parse）。
   *
   * @param themeId   主题
   * @param channelId 渠道
   * @param url       视频 URL（须可被识别为视频）
   * @param title     标题
   */
  public void seedPendingSourceVideo(
      String themeId, String channelId, String url, String title) {
    if (hotNews == null) {
      throw new IllegalStateException("HotNewsFacade 未绑定");
    }
    FetchedItemView item =
        FetchedItemView.builder()
            .url(url)
            .title(title)
            .summary("Golden source video about sleep and melatonin")
            .publishedAt(LocalDateTime.now().minusHours(2))
            .build();
    hotNews.persistFetchedItems(themeId, channelId, List.of(item));
  }

  /**
   * 将公众号 API base 指向外部 WireMock（依赖应用已支持动态配置时由场景再写设置；
   * 黄金 IT 通常用 {@code workbench.wechat-mp.api-base} 动态属性）。
   *
   * @return 外部桩 baseUrl；未绑定则空串
   */
  public String externalBaseUrl() {
    return externalServer == null ? "" : externalServer.baseUrl();
  }

  /**
   * 搜索基座（DuckDuckGo）URL。
   *
   * @return baseUrl
   */
  public String searchBaseUrl() {
    return externalBaseUrl();
  }

  @Override
  public List<Map<String, Object>> jobEventHistory(String jobId) {
    try {
      return getJsonList("/api/jobs/" + jobId + "/event-history");
    } catch (Exception exception) {
      throw new IllegalStateException("读取任务事件失败 jobId=" + jobId, exception);
    }
  }

  /**
   * 导出任务事件 jsonl。
   *
   * @param jobId 任务 ID
   * @return jsonl
   */
  public String exportJobEventsJsonl(String jobId) {
    return new JobEventRecorder(this).exportJsonl(jobId);
  }

  /**
   * @return 应用 base URL
   */
  public String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  /**
   * GET JSON 对象。
   *
   * @param path 路径
   * @return data map
   * @throws Exception 失败
   */
  public Map<String, Object> getJson(String path) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    requireOk("GET", path, response);
    return unwrapApiDataAsMap(response.body());
  }

  /**
   * GET JSON 列表。
   *
   * @param path 路径
   * @return data list
   * @throws Exception 失败
   */
  public List<Map<String, Object>> getJsonList(String path) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    requireOk("GET", path, response);
    Object data = unwrapApiData(response.body());
    if (data instanceof List<?> list) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> cast = (List<Map<String, Object>>) list;
      return cast;
    }
    throw new IllegalStateException("Expected list data for " + path + ": " + response.body());
  }

  /**
   * POST JSON。
   *
   * @param path 路径
   * @param body 体；可空
   * @return data map
   * @throws Exception 失败
   */
  public Map<String, Object> postJson(String path, Object body) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl() + path)).header("Content-Type", "application/json");
    if (body == null) {
      builder.POST(HttpRequest.BodyPublishers.noBody());
    } else {
      builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
    }
    HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    requireOk("POST", path, response);
    return unwrapApiDataAsMap(response.body());
  }

  /**
   * PUT JSON。
   *
   * @param path 路径
   * @param body 体
   * @return data map
   * @throws Exception 失败
   */
  public Map<String, Object> putJson(String path, Object body) throws Exception {
    String json = objectMapper.writeValueAsString(body);
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    requireOk("PUT", path, response);
    return unwrapApiDataAsMap(response.body());
  }

  /**
   * PATCH JSON。
   *
   * @param path 路径
   * @param body 体
   * @return data map
   * @throws Exception 失败
   */
  public Map<String, Object> patchJson(String path, Object body) throws Exception {
    String json = objectMapper.writeValueAsString(body == null ? Map.of() : body);
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    requireOk("PATCH", path, response);
    return unwrapApiDataAsMap(response.body());
  }

  /**
   * DELETE。
   *
   * @param path 路径
   * @return data map
   * @throws Exception 失败
   */
  public Map<String, Object> deleteJson(String path) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(baseUrl() + path)).DELETE().build(),
            HttpResponse.BodyHandlers.ofString());
    requireOk("DELETE", path, response);
    if (StrUtil.isBlank(response.body())) {
      return Map.of("ok", true);
    }
    return unwrapApiDataAsMap(response.body());
  }

  /**
   * 等待任务终态。
   *
   * @param jobId          任务 ID
   * @param timeoutSeconds 超时秒
   * @return 终态任务视图
   */
  public Map<String, Object> awaitTerminal(String jobId, long timeoutSeconds) {
    await()
        .atMost(timeoutSeconds, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> {
              Map<String, Object> job = getJson("/api/jobs/" + jobId);
              return List.of("succeeded", "failed", "cancelled")
                  .contains(String.valueOf(job.get("status")));
            });
    try {
      return getJson("/api/jobs/" + jobId);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  /**
   * 从创建任务响应中解析 jobId。
   *
   * @param response 响应
   * @return jobId
   */
  public static String extractJobId(Map<String, Object> response) {
    if (response == null) {
      throw new IllegalStateException("空任务响应");
    }
    Object id = response.get("id");
    if (id == null) {
      id = response.get("jobId");
    }
    if (id == null) {
      throw new IllegalStateException("响应无 jobId: " + response);
    }
    return String.valueOf(id);
  }

  /**
   * 将对象序列化为稳定 JSON（产物采集用）。
   *
   * @param value 值
   * @return JSON 文本
   */
  public String toJson(Object value) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (Exception exception) {
      return JSONUtil.toJsonPrettyStr(value);
    }
  }

  /**
   * 组装四类原始产物（录制器尚未接 WireMock 日志时 AI/外部为空）。
   *
   * @param eventsJsonl 事件
   * @param outputs     业务产物对象
   * @return artifacts
   */
  public Map<String, String> packArtifacts(String eventsJsonl, Object outputs) {
    Map<String, String> artifacts = new LinkedHashMap<>();
    artifacts.put("ai", aiRecorder.exportJsonl());
    artifacts.put("external", externalRecorder.exportJsonl());
    artifacts.put("events", StrUtil.nullToEmpty(eventsJsonl));
    artifacts.put("outputs", toJson(outputs == null ? Map.of() : outputs));
    return artifacts;
  }

  private void cleanBusinessTables() {
    // 与 WorkbenchIT 对齐的业务表清理（保留 skills/workflows 种子）
    String[] tables = {
      "job_events",
      "workflow_artifacts",
      "workflow_node_runs",
      "workflow_alerts",
      "workflow_templates",
      "workflow_runs",
      "jobs",
      "notifications",
      "human_reviews",
      "publication_metric_snapshots",
      "publications",
      "output_layouts",
      "render_snapshots",
      "channel_accounts",
      "repair_candidate_issues",
      "repair_candidates",
      "audit_issue_refs",
      "audit_issues",
      "audit_runs",
      "skill_node_bindings",
      "video_transcripts",
      "hotnews_research_item_dimensions",
      "hotnews_research_conflicts",
      "hotnews_research_items",
      "hotnews_research_runs",
      "hotnews_work_sources",
      "hotnews_works",
      "hotnews_brief_sources",
      "hotnews_brief_items",
      "hotnews_briefs",
      "hotnews_article_skeleton_items",
      "hotnews_article_skeletons",
      "hotnews_topic_score_tags",
      "hotnews_topic_score_dimensions",
      "hotnews_topic_score_metrics",
      "hotnews_topic_sources",
      "hotnews_topics",
      "hotnews_radar_batches",
      "source_query_site_block_samples",
      "source_query_site_blocks",
      "source_items",
      "source_fetch_channel_runs",
      "source_fetch_runs",
      "source_channel_values",
      "source_channels",
      "repair_run_codes",
      "repair_runs",
      "media_generations",
      "media_assets",
      "work_output_versions",
      "course_output_sources",
      "work_outputs",
      "course_lesson_outcomes",
      "work_generation_settings",
      "course_lessons",
      "works",
      "course_series",
      "course_curriculum_draft_outcomes",
      "course_curriculum_draft_lessons",
      "course_curriculum_draft_series",
      "theme_scoring_rubric_dimensions",
      "theme_scoring_rubrics",
      "theme_profile_items",
      "theme_profiles",
      "course_curricula",
      "themes",
      "stored_files",
      "model_calls"
    };
    for (String table : tables) {
      try {
        jdbc.update("DELETE FROM " + table);
      } catch (Exception ignored) {
        // 表不存在或 FK 顺序问题时跳过，后续场景会暴露
      }
    }
  }

  private static void requireOk(String method, String path, HttpResponse<String> response) {
    if (response.statusCode() >= 400) {
      throw new IllegalStateException(
          method + " " + path + " failed: " + response.statusCode() + " " + response.body());
    }
  }

  private Object unwrapApiData(String body) throws Exception {
    Map<String, Object> root = objectMapper.readValue(body, new TypeReference<>() {});
    if (root.containsKey("code") && root.containsKey("data")) {
      return root.get("data");
    }
    return root;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> unwrapApiDataAsMap(String body) throws Exception {
    Object data = unwrapApiData(body);
    if (data instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    if (data == null) {
      return Map.of();
    }
    return objectMapper.convertValue(data, new TypeReference<>() {});
  }
}
