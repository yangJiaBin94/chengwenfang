package com.chengwenfang.app.bootstrap;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiSettingsFacade;
import com.chengwenfang.ai.facade.dto.view.AiSettingsView;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.infra.support.OsPythonExecutableResolver;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import com.chengwenfang.prompt.facade.dto.view.BuiltinInspectView;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 在现有内置配置体检之外，探测 Qdrant、密钥、热点 CLI 与可选开关。
 * <p>不另建清单接口；结果并入 {@code /api/system/builtin-ready}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemReadinessProbe {
  /** 向量库（内存实现 {@code degraded=true}）。 */
  private final KnowledgeVectorStore vectorStore;
  /** 图库（默认空实现）。 */
  private final KnowledgeGraphStore graphStore;
  /** AI 配置（只看是否已配置，不取明文）。 */
  private final AiSettingsFacade aiSettingsFacade;
  /** 全局配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 收集全部检查项。
   *
   * @param builtin 内置提示词包 / 工作流体检
   * @return 检查项，顺序即面板顺序
   */
  public List<SystemReadinessItem> collect(BuiltinInspectView builtin) {
    List<SystemReadinessItem> items = new ArrayList<>();
    int promptPackCount = builtin == null ? 0 : builtin.getPromptPackCount();
    int expected = builtin == null ? 0 : builtin.getExpectedPromptPackCount();
    boolean builtinReady = builtin != null && builtin.isReady();
    boolean workflowReady = builtin != null && builtin.isWorkflowReady();
    boolean promptReady = SystemReadinessRules.promptPacksReady(
        promptPackCount, expected, builtinReady, workflowReady);
    items.add(SystemReadinessRules.item(
        SystemReadinessKeys.PROMPT_PACKS,
        "内置提示词包",
        SystemReadinessSection.DEFAULT_PROFILE,
        promptReady ? SystemReadinessStatus.PASS : SystemReadinessStatus.FAIL,
        "库内 " + promptPackCount + " / 期望 " + expected,
        true));
    items.add(SystemReadinessRules.item(
        SystemReadinessKeys.WORKFLOWS,
        "三条主工作流",
        SystemReadinessSection.DEFAULT_PROFILE,
        workflowReady ? SystemReadinessStatus.PASS : SystemReadinessStatus.FAIL,
        workflowReady ? "课程生成、热点成文、雷达采集均已发布" : "有主工作流未发布或节点不齐",
        true));

    ChengwenfangProperties.QdrantSettings qdrant = properties.getQdrant();
    boolean qdrantEnabled = qdrant != null && qdrant.isEnabled();
    boolean degraded = vectorStore == null || vectorStore.degraded();
    items.add(SystemReadinessRules.qdrant(
        qdrantEnabled,
        degraded,
        qdrant == null ? "" : qdrant.getHost(),
        qdrant == null ? 0 : qdrant.getGrpcPort()));

    AiSettingsView settings = loadSettings();
    items.add(SystemReadinessRules.apiKey(
        SystemReadinessKeys.GENERATION_KEY,
        "生成 Key",
        settings == null ? null : settings.getVision(),
        "未配置。课程与热点成文停在生成步；请在设置里填写多模态供应商并测试连接。种子不会写入密钥。"));
    items.add(SystemReadinessRules.apiKey(
        SystemReadinessKeys.EMBEDDING_KEY,
        "嵌入 Key",
        settings == null ? null : settings.getEmbedding(),
        "未配置。知识库入库和问答不会成功，请在设置里填写嵌入模型并测试连接。"));
    items.add(SystemReadinessRules.apiKey(
        SystemReadinessKeys.IMAGE_KEY,
        "图像 Key",
        settings == null ? null : settings.getImage(),
        "未配置。配图与封面会停住；请在设置里填写图片供应商并测试连接。"));

    ChengwenfangProperties.ArticleExtractSettings article = properties.getArticleExtract();
    items.add(probeCli(
        SystemReadinessKeys.CLI_ARTICLE_EXTRACT,
        "article-extract",
        article == null ? "scripts/article-extract" : article.getWorkingDirectory(),
        article == null ? "python" : article.getPythonExecutable(),
        List.of("trafilatura"),
        "热点正文抽取会缺 Trafilatura；课程成文与知识库不依赖它。"));
    ChengwenfangProperties.VideoTranscriptSettings video = properties.getVideoTranscript();
    items.add(probeCli(
        SystemReadinessKeys.CLI_VIDEO_TRANSCRIPT,
        "video-transcript",
        video == null ? "scripts/video-transcript" : video.getWorkingDirectory(),
        video == null ? "python" : video.getPythonExecutable(),
        List.of("whisper"),
        "资料池视频解析会缺；课程成文与知识库不依赖它。"));
    ChengwenfangProperties.PageScreenshotSettings page = properties.getPageScreenshot();
    items.add(probeCli(
        SystemReadinessKeys.CLI_PAGE_SCREENSHOT,
        "page-screenshot",
        page == null ? "scripts/page-screenshot" : page.getWorkingDirectory(),
        page == null ? "python" : page.getPythonExecutable(),
        List.of("playwright", "fitz"),
        "佐证截图与 Vision 兜底会缺；找不到定位文本时也不会冒充首屏。课程主路径不依赖它。"));

    ChengwenfangProperties.ReadinessSettings readiness = properties.getReadiness();
    String searxngUrl = readiness == null
        ? "http://127.0.0.1:8080"
        : StrUtil.blankToDefault(readiness.getSearxngUrl(), "http://127.0.0.1:8080");
    items.add(SystemReadinessRules.addonService(
        SystemReadinessKeys.SEARXNG,
        "SearXNG",
        searxngReachable(searxngUrl),
        trimSlash(searxngUrl),
        "热点雷达的本地聚合搜索会缺；未加装不是故障。"));
    String lightpandaHost = readiness == null
        ? "127.0.0.1"
        : StrUtil.blankToDefault(readiness.getLightpandaHost(), "127.0.0.1");
    int lightpandaPort = readiness == null || readiness.getLightpandaPort() <= 0
        ? 9222
        : readiness.getLightpandaPort();
    items.add(SystemReadinessRules.addonService(
        SystemReadinessKeys.LIGHTPANDA,
        "Lightpanda",
        tcpOpen(lightpandaHost, lightpandaPort),
        lightpandaHost + ":" + lightpandaPort,
        "JS 页正文回退会缺。镜像为 AGPL 边车，课程与知识库不依赖它。"));

    boolean graphEnabled = graphStore != null && graphStore.enabled();
    boolean graphAvailable = graphStore != null && graphStore.available();
    items.add(SystemReadinessRules.optionalSwitch(
        SystemReadinessKeys.GRAPH,
        "Graph",
        graphEnabled,
        graphAvailable,
        "默认关闭。满血档可以拉起 Neo4j 容器，应用不会因此打开图检索。",
        "已开启且可连接。",
        "已开启但 Neo4j 未连通。"));
    boolean agentEnabled = properties.getKnowledge() != null
        && properties.getKnowledge().getAgent() != null
        && properties.getKnowledge().getAgent().isEnabled();
    items.add(SystemReadinessRules.optionalSwitch(
        SystemReadinessKeys.AGENT,
        "Agent",
        agentEnabled,
        agentEnabled,
        "默认关闭，不参与知识库问答。",
        "已开启。",
        "已开启但不可用。"));
    return items;
  }

  /**
   * 读取 AI 公开配置；失败时各项 Key 记为未配置。
   *
   * @return 配置；失败为 null
   */
  private AiSettingsView loadSettings() {
    try {
      return aiSettingsFacade.getSettings();
    } catch (Exception exception) {
      log.warn("读取 AI 配置失败，密钥项记为未配置：{}", exception.getMessage());
      return null;
    }
  }

  /**
   * 按操作系统解析 venv，并确认声明的模块能被找到。
   *
   * @param key               稳定键
   * @param label             标题
   * @param workingDirectory  CLI 目录
   * @param configuredPython  配置中的解释器
   * @param modules           需可导入的模块
   * @param purpose           未加装时缺什么
   * @return 检查项
   */
  private SystemReadinessItem probeCli(
      String key,
      String label,
      String workingDirectory,
      String configuredPython,
      List<String> modules,
      String purpose) {
    Path python = OsPythonExecutableResolver.resolve(
        Path.of(StrUtil.blankToDefault(properties.getContentRoot(), ".")),
        workingDirectory,
        configuredPython);
    boolean present = Files.isRegularFile(python);
    boolean importsOk = present && modulesImport(python, modules);
    return SystemReadinessRules.cli(key, label, present, importsOk, python.toString(), purpose);
  }

  /**
   * 用解释器的 find_spec 检查模块，避免导入 Whisper 权重。
   *
   * @param python  解释器
   * @param modules 模块名
   * @return 全部可找到则为 true
   */
  private boolean modulesImport(Path python, List<String> modules) {
    List<String> command = new ArrayList<>();
    command.add(python.toString());
    command.add("-c");
    command.add(
        "import importlib.util,sys; missing=[name for name in sys.argv[1:] if importlib.util.find_spec(name) is None]; sys.exit(1 if missing else 0)");
    command.addAll(modules);
    Process process = null;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
      int timeout = properties.getReadiness() == null
          ? 8
          : Math.max(2, properties.getReadiness().getCliProbeTimeoutSeconds());
      boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return false;
      }
      return process.exitValue() == 0;
    } catch (Exception exception) {
      if (process != null) {
        process.destroyForcibly();
      }
      log.info("CLI 依赖探测失败 python={} err={}", python, exception.getMessage());
      return false;
    }
  }

  /**
   * 探测 SearXNG {@code /healthz}。
   *
   * @param baseUrl 实例根地址
   * @return 2xx 则为 true
   */
  private boolean searxngReachable(String baseUrl) {
    String url = trimSlash(baseUrl) + "/healthz";
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(2))
          .GET()
          .build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() >= 200 && response.statusCode() < 300;
    } catch (Exception exception) {
      log.info("SearXNG 未连通 url={} err={}", url, exception.getMessage());
      return false;
    }
  }

  /**
   * TCP 探测 Lightpanda 端口。
   *
   * @param host 主机
   * @param port 端口
   * @return 能连上则为 true
   */
  private boolean tcpOpen(String host, int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 1500);
      return true;
    } catch (IOException exception) {
      log.info("Lightpanda 未连通 {}:{} {}", host, port, exception.getMessage());
      return false;
    }
  }

  private static String trimSlash(String url) {
    String value = StrUtil.blankToDefault(url, "").trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }
}
