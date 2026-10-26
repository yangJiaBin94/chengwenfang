package com.workbench.app.support;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.app.WorkbenchApplication;
import com.workbench.content.facade.constant.OutputTrackKinds;
import com.workbench.job.facade.JobFacade;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = WorkbenchApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class WorkbenchIT {
  private static final boolean USE_TESTCONTAINERS;
  private static final MySQLContainer<?> MYSQL;
  private static final GenericContainer<?> REDIS;

  static {
    maybeConfigureWindowsDockerHost();
    MySQLContainer<?> mysql = null;
    GenericContainer<?> redis = null;
    boolean dockerOk = false;
    try {
      if (!DockerClientFactory.instance().isDockerAvailable()) {
        throw new IllegalStateException("Docker is not available");
      }
      // 容器由静态字段持有至 JVM 结束；失败路径在 stopQuietly 关闭
      mysql = newMysqlContainer();
      redis = newRedisContainer();
      mysql.start();
      redis.start();
      dockerOk = true;
    } catch (Throwable ex) {
      stopQuietly(mysql);
      stopQuietly(redis);
      mysql = null;
      redis = null;
      System.err.println(
          "Testcontainers unavailable (" + ex.getClass().getSimpleName() + ": " + ex.getMessage()
              + "); falling back to host MySQL:3306 / Redis:6379 database workbench_it");
    }
    USE_TESTCONTAINERS = dockerOk;
    MYSQL = mysql;
    REDIS = redis;
  }

  /**
   * 创建 IT 用 MySQL 容器（有意不在方法内 close，交给静态字段 / Ryuk）。
   *
   * @return MySQL 容器
   */
  @SuppressWarnings("resource")
  private static MySQLContainer<?> newMysqlContainer() {
    return new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
        .withDatabaseName("workbench_it")
        .withUsername("workbench")
        .withPassword("workbench_dev");
  }

  /**
   * 创建 IT 用 Redis 容器（有意不在方法内 close，交给静态字段 / Ryuk）。
   *
   * @return Redis 容器
   */
  @SuppressWarnings("resource")
  private static GenericContainer<?> newRedisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
  }

  /**
   * 启动失败时尽力停止容器，吞掉停止异常。
   *
   * @param container 可为 null
   */
  private static void stopQuietly(GenericContainer<?> container) {
    if (container == null) {
      return;
    }
    try {
      container.stop();
    } catch (Exception ignored) {
      // ignore
    }
  }

  /**
   * Windows 上为 Testcontainers / docker-java 选择可用的 Docker Desktop named pipe，
   * 并钉住 API 版本以免 Engine 29 对旧客户端返回 400。
   */
  private static void maybeConfigureWindowsDockerHost() {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (!os.contains("win")) {
      return;
    }
    // Engine 29 + 旧 docker-java 常在 /info 上 400；钉 API 1.44 可互通
    if (System.getenv("DOCKER_API_VERSION") == null
        && System.getProperty("DOCKER_API_VERSION") == null) {
      System.setProperty("DOCKER_API_VERSION", "1.44");
    }
    // 容器映射到宿主机时用 localhost，避免 npipe 场景下错误的 host 探测
    if (System.getenv("TESTCONTAINERS_HOST_OVERRIDE") == null
        && System.getProperty("TESTCONTAINERS_HOST_OVERRIDE") == null) {
      System.setProperty("TESTCONTAINERS_HOST_OVERRIDE", "localhost");
    }
    // 覆盖 ~/.testcontainers.properties 里钉死的 NpipeSocket…Strategy（默认 docker_engine，
    // 在 Docker Desktop 4.x 上常返回空 Info / 400）；改走 DOCKER_HOST 显式策略
    System.setProperty(
        "docker.client.strategy",
        "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy");
    if (System.getenv("DOCKER_HOST") != null || System.getProperty("DOCKER_HOST") != null) {
      return;
    }
    // Docker Desktop 上下文常见 pipe；按 CLI desktop-linux 优先，再回退旧名
    String[] pipeCandidates = {
      "npipe:////./pipe/dockerDesktopLinuxEngine",
      "npipe:////./pipe/docker_engine",
      "npipe:////./pipe/dockerDesktopEngine",
      "npipe:////./pipe/docker_engine_linux"
    };
    for (String pipe : pipeCandidates) {
      if (windowsNamedPipeExists(pipe)) {
        System.setProperty("DOCKER_HOST", pipe);
        return;
      }
    }
    System.setProperty("DOCKER_HOST", pipeCandidates[0]);
  }

  /**
   * 判断 Windows named pipe 路径是否存在于 {@code \\.\pipe\}。
   *
   * @param dockerHost {@code npipe:////./pipe/Name} 形态
   * @return 存在则为 true
   */
  private static boolean windowsNamedPipeExists(String dockerHost) {
    if (dockerHost == null || !dockerHost.startsWith("npipe:////./pipe/")) {
      return false;
    }
    String pipeName = dockerHost.substring("npipe:////./pipe/".length());
    return new java.io.File("\\\\.\\pipe\\" + pipeName).exists();
  }

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    if (USE_TESTCONTAINERS) {
      registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
      registry.add("spring.datasource.username", MYSQL::getUsername);
      registry.add("spring.datasource.password", MYSQL::getPassword);
      registry.add("spring.data.redis.host", REDIS::getHost);
      registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    } else {
      ensureHostItDatabase();
      // Host compose fallback: isolated workbench_it DB (avoids racing a local workbench-app worker).
      registry.add(
          "spring.datasource.url",
          () ->
              "jdbc:mysql://127.0.0.1:3306/workbench_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8");
      registry.add("spring.datasource.username", () -> "workbench");
      registry.add("spring.datasource.password", () -> "workbench_dev");
      registry.add("spring.data.redis.host", () -> "127.0.0.1");
      registry.add("spring.data.redis.port", () -> 6379);
    }
  }

  private static void ensureHostItDatabase() {
    try {
      ProcessBuilder pb = new ProcessBuilder(
          "docker",
          "exec",
          "workbench-mysql-1",
          "mysql",
          "-uroot",
          "-pworkbench_root",
          "-e",
          "CREATE DATABASE IF NOT EXISTS workbench_it CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; "
              + "GRANT ALL PRIVILEGES ON workbench_it.* TO 'workbench'@'%'; FLUSH PRIVILEGES;");
      pb.redirectErrorStream(true);
      Process process = pb.start();
      process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
    } catch (Exception ex) {
      System.err.println("Could not ensure workbench_it via docker exec: " + ex.getMessage());
    }
  }

  @LocalServerPort
  protected int port;

  @Autowired
  protected JobFacade jobs;

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected JdbcTemplate jdbc;

  protected final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @BeforeEach
  void cleanJobs() {
    // 先取消未终态任务并等 Worker 放行，避免删 workflow_runs 时仍有 FK 写入导致死锁/job_not_found
    try {
      jdbc.update(
          "UPDATE jobs SET status = 'cancelled', updated_at = UTC_TIMESTAMP(3) "
              + "WHERE status IN ('pending', 'running', 'queued')");
      await()
          .atMost(15, TimeUnit.SECONDS)
          .pollInterval(100, TimeUnit.MILLISECONDS)
          .until(
              () -> {
                Integer running =
                    jdbc.queryForObject(
                        "SELECT COUNT(*) FROM jobs WHERE status = 'running'", Integer.class);
                return running == null || running == 0;
              });
    } catch (Exception ignored) {
      // 清理前置尽力而为；后续 DELETE 仍会执行
    }
    jdbc.update("DELETE FROM job_events");
    jdbc.update("DELETE FROM workflow_artifacts");
    jdbc.update("DELETE FROM workflow_node_runs");
    jdbc.update("DELETE FROM workflow_run_executed_nodes");
    jdbc.update("DELETE FROM workflow_alerts");
    jdbc.update("DELETE FROM workflow_templates");
    jdbc.update("DELETE FROM workflow_runs");
    jdbc.update("DELETE FROM jobs");
    jdbc.update("DELETE FROM notifications");
    jdbc.update("DELETE FROM human_reviews");
    jdbc.update("DELETE FROM publication_metric_snapshots");
    jdbc.update("DELETE FROM publications");
    jdbc.update("DELETE FROM output_layouts");
    jdbc.update("DELETE FROM render_snapshots");
    jdbc.update("DELETE FROM channel_accounts");
    jdbc.update("DELETE FROM repair_candidate_issues");
    jdbc.update("DELETE FROM repair_candidates");
    jdbc.update("DELETE FROM audit_issue_refs");
    jdbc.update("DELETE FROM audit_issues");
    jdbc.update("DELETE FROM audit_runs");
    jdbc.update("DELETE FROM skill_default_bindings");
    jdbc.update("DELETE FROM model_calls");
    jdbc.update("DELETE FROM video_transcripts");
    jdbc.update("DELETE FROM hotnews_research_item_dimensions");
    jdbc.update("DELETE FROM hotnews_research_conflicts");
    jdbc.update("DELETE FROM hotnews_research_items");
    jdbc.update("DELETE FROM hotnews_research_runs");
    jdbc.update("DELETE FROM hotnews_work_sources");
    jdbc.update("DELETE FROM hotnews_works");
    jdbc.update("DELETE FROM hotnews_brief_sources");
    jdbc.update("DELETE FROM hotnews_brief_items");
    jdbc.update("DELETE FROM hotnews_briefs");
    jdbc.update("DELETE FROM hotnews_article_skeleton_items");
    jdbc.update("DELETE FROM hotnews_article_skeletons");
    jdbc.update("DELETE FROM hotnews_topic_score_tags");
    jdbc.update("DELETE FROM hotnews_topic_score_dimensions");
    jdbc.update("DELETE FROM hotnews_topic_score_metrics");
    jdbc.update("DELETE FROM hotnews_topic_sources");
    jdbc.update("DELETE FROM hotnews_topics");
    jdbc.update("DELETE FROM hotnews_radar_batches");
    jdbc.update("DELETE FROM source_query_site_block_samples");
    jdbc.update("DELETE FROM source_query_site_blocks");
    jdbc.update("DELETE FROM source_items");
    jdbc.update("DELETE FROM source_fetch_channel_runs");
    jdbc.update("DELETE FROM source_fetch_runs");
    jdbc.update("DELETE FROM source_channel_values");
    jdbc.update("DELETE FROM source_channels");
    jdbc.update("DELETE FROM repair_run_codes");
    jdbc.update("DELETE FROM repair_runs");
    jdbc.update("DELETE FROM media_generations");
    jdbc.update("DELETE FROM media_assets");
    jdbc.update("DELETE FROM work_output_versions");
    jdbc.update("DELETE FROM course_output_sources");
    jdbc.update("DELETE FROM work_outputs");
    jdbc.update("DELETE FROM course_lesson_outcomes");
    jdbc.update("DELETE FROM work_generation_settings");
    jdbc.update("DELETE FROM course_lessons");
    jdbc.update("DELETE FROM works");
    jdbc.update("DELETE FROM course_series");
    jdbc.update("DELETE FROM stored_files");
  }

  /**
   * 写入热点作品双轨产出壳（works / hotnews_works / work_outputs / settings）。
   *
   * @param articleId               热点文章 id（= works.id）
   * @param topicId                 选题 id
   * @param briefId                 brief id
   * @param title                   标题
   * @param contentHash             内容哈希
   * @param wordCount               字数
   * @param status                  状态
   * @param generationSettingsJson  生成参数 JSON
   * @param sourceSnapshotJson      来源快照 JSON（本种子仅接受空数组）
   */
  protected void seedHotNewsArticleDocuments(
      String articleId,
      String topicId,
      String briefId,
      String title,
      String contentHash,
      int wordCount,
      String status,
      String generationSettingsJson,
      String sourceSnapshotJson) {
    String themeId = jdbc.queryForObject(
        "SELECT theme_id FROM hotnews_topics WHERE id = ?", String.class, topicId);
    jdbc.update(
        "INSERT INTO works (id, mode_code, theme_id, title, created_at, updated_at) "
            + "VALUES (?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        articleId,
        "hot_news",
        themeId,
        title);
    jdbc.update(
        "INSERT INTO hotnews_works (work_id, topic_id, brief_id, status) VALUES (?,?,?,?)",
        articleId,
        topicId,
        briefId,
        status);
    seedHotNewsOutputTrack(
        articleId, OutputTrackKinds.HOT_DRAFT, "master", title, contentHash, wordCount, status);
    seedHotNewsOutputTrack(
        articleId, OutputTrackKinds.HOT_WECHAT, "wechat_mp", title, contentHash, wordCount, status);
    String wordOption = null;
    if (generationSettingsJson != null && generationSettingsJson.contains("wordCountOption")) {
      int start = generationSettingsJson.indexOf("wordCountOption");
      int colon = generationSettingsJson.indexOf(':', start);
      int quote1 = generationSettingsJson.indexOf('"', colon + 1);
      int quote2 = generationSettingsJson.indexOf('"', quote1 + 1);
      if (quote1 >= 0 && quote2 > quote1) {
        wordOption = generationSettingsJson.substring(quote1 + 1, quote2);
      }
    }
    jdbc.update(
        "INSERT INTO work_generation_settings (work_id, word_count_option, updated_at) "
            + "VALUES (?,?,UTC_TIMESTAMP(3))",
        articleId,
        wordOption);
  }

  /**
   * 写入单轨热点产出壳（无正文版本，current_version_no=0）。
   *
   * @param articleId   作品 id
   * @param kind        draft / wechat
   * @param channelCode master / wechat_mp
   * @param title       标题
   * @param contentHash 哈希
   * @param wordCount   字数
   * @param status      状态
   */
  private void seedHotNewsOutputTrack(
      String articleId,
      String kind,
      String channelCode,
      String title,
      String contentHash,
      int wordCount,
      String status) {
    // 测试种子用随机产出 id；业务侧按 work_id+channel 查库，不再派生
    String outputId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO work_outputs (id, work_id, channel_code, format_code, title, status, "
            + "current_version_no, content_hash, word_count, version_token, created_at, updated_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
        outputId,
        articleId,
        channelCode,
        "article",
        title,
        status,
        0,
        contentHash,
        wordCount,
        0L);
  }

  protected String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  protected Map<String, Object> getJson(String path) throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("GET " + path + " failed: " + response.statusCode() + " " + response.body());
    }
    return unwrapApiDataAsMap(response.body());
  }

  protected List<Map<String, Object>> getJsonList(String path) throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("GET " + path + " failed: " + response.statusCode() + " " + response.body());
    }
    Object data = unwrapApiData(response.body());
    if (data instanceof List<?> list) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> cast = (List<Map<String, Object>>) list;
      return cast;
    }
    throw new IllegalStateException("Expected list data for " + path + ": " + response.body());
  }

  protected Map<String, Object> putJson(String path, Object body) throws Exception {
    String json = objectMapper.writeValueAsString(body);
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl() + path))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("PUT " + path + " failed: " + response.statusCode() + " " + response.body());
    }
    return unwrapApiDataAsMap(response.body());
  }

  protected Map<String, Object> patchJson(String path, Object body) throws Exception {
    String json = objectMapper.writeValueAsString(body == null ? Map.of() : body);
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl() + path))
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
            .build(),
        HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("PATCH " + path + " failed: " + response.statusCode() + " " + response.body());
    }
    return unwrapApiDataAsMap(response.body());
  }

  protected Map<String, Object> deleteJson(String path) throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl() + path)).DELETE().build(),
        HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("DELETE " + path + " failed: " + response.statusCode() + " " + response.body());
    }
    if (response.body() == null || response.body().isBlank()) {
      return Map.of("ok", true);
    }
    return unwrapApiDataAsMap(response.body());
  }

  protected Map<String, Object> postJson(String path) throws Exception {
    return postJson(path, null);
  }

  protected Map<String, Object> postJson(String path, Object body) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
        .header("Content-Type", "application/json");
    if (body == null) {
      builder.POST(HttpRequest.BodyPublishers.noBody());
    } else {
      builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
    }
    HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("POST " + path + " failed: " + response.statusCode() + " " + response.body());
    }
    return unwrapApiDataAsMap(response.body());
  }

  /** 解析 ApiResult 信封，返回 data；兼容非信封旧响应。 */
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

  protected Map<String, Object> getJob(String jobId) throws Exception {
    return getJson("/api/jobs/" + jobId);
  }

  protected List<Map<String, Object>> eventHistory(String jobId) throws Exception {
    return getJsonList("/api/jobs/" + jobId + "/event-history");
  }

  protected Map<String, Object> awaitTerminal(String jobId) {
    return awaitTerminal(jobId, 20);
  }

  protected Map<String, Object> awaitTerminal(String jobId, long timeoutSeconds) {
    await().atMost(timeoutSeconds, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
      Map<String, Object> job = getJob(jobId);
      return List.of("succeeded", "failed", "cancelled").contains(String.valueOf(job.get("status")));
    });
    try {
      return getJob(jobId);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  protected void pointFormalAiProviders(com.github.tomakehurst.wiremock.WireMockServer server) throws Exception {
    FormalAiWireMock.pointProviders(this::putJsonSettingsAi, server);
  }

  private Map<String, Object> putJsonSettingsAi(Object body) throws Exception {
    return putJson("/api/settings/ai", body);
  }

  protected void expireLease(String jobId) {
    jdbc.update(
        "UPDATE jobs SET lease_expires_at = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 5 SECOND) WHERE id = ?",
        jobId);
  }
}
