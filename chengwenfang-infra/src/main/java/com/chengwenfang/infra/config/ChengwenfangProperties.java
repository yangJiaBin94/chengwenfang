package com.chengwenfang.infra.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 成文坊全局配置（{@code chengwenfang.*}）：运行目录、Worker 调度、爬虫与搜索等。
 */
@Data
@ConfigurationProperties(prefix = "chengwenfang")
public class ChengwenfangProperties {
  private String appName = "chengwenfang-backend";
  private String appVersion = "0.1.0";
  private String environment = "development";
  /** 运行时数据目录（生成图、缓存等），默认 {@code .chengwenfang}。 */
  private String runtimeDir = ".chengwenfang";
  /** 仓库根路径，用于解析 content/ 与 public/ 媒体文件。 */
  private String contentRoot = ".";
  /** Worker 轮询待认领任务的间隔（秒）。 */
  private double workerPollSeconds = 1.0;
  /** 任务租约时长（秒）；超时未续约可被重新认领。 */
  private int workerLeaseSeconds = 300;
  private int workerHeartbeatSeconds = 30;
  /**
   * Worker 全局并发上限（总槽）。
   * <p>与分类型槽同时生效：任务须同时占到全局槽与类型槽；本值亦作未配置类型槽时的回退上限。
   */
  private int workerConcurrency = 2;
  private int workerDefaultTaskTimeoutSeconds = 1800;
  private int workerCourseTaskTimeoutSeconds = 7200;
  private int workerFetchTaskTimeoutSeconds = 900;
  /** 文本类 Job 并发槽（成文 / 审核 / 修复等）。 */
  private int workerTextConcurrency = 2;
  /** 配图类 Job 并发槽。 */
  private int workerImageConcurrency = 1;
  /** 采集 / 调研 / 视频解析类 Job 并发槽。 */
  private int workerFetchConcurrency = 2;
  /** SSE 事件轮询间隔（秒）。 */
  private double ssePollSeconds = 0.5;
  /** 是否启动进程内 Job Worker。 */
  private boolean workerEnabled = true;
  /** 为 true 时允许爬虫访问环回/私网主机（仅测试）。 */
  private boolean crawlerAllowLoopback = false;
  /** HTML 搜索基址（默认 DuckDuckGo；测试可用 WireMock）。 */
  private String searchBaseUrl = "https://html.duckduckgo.com";
  /** 图像生成模型单次调用超时（秒），来自 application.yml，不走库表。 */
  private int aiImageTimeoutSeconds = 180;
  /** 多模态补全模型单次调用超时（秒），来自 application.yml，不走库表。 */
  private int aiVisionTimeoutSeconds = 180;
  /** 文本嵌入模型单次调用超时（秒），来自 application.yml，不走库表。 */
  private int aiEmbeddingTimeoutSeconds = 60;
  /** 知识库向量库（Qdrant）连接与集合配置。 */
  private QdrantSettings qdrant = new QdrantSettings();
  /** 系统就绪面板探测的可选热点依赖地址。 */
  private ReadinessSettings readiness = new ReadinessSettings();
  /** 知识库 RAG 功能开关与 Agent 预算。 */
  private KnowledgeSettings knowledge = new KnowledgeSettings();
  /** Neo4j 图库连接（Graph flag 打开时使用）。 */
  private Neo4jSettings neo4j = new Neo4jSettings();
  /** 线程池相关配置（渠道采集等）。 */
  private ExecutorSettings executor = new ExecutorSettings();
  /** 资料池视频解析（本机 Python CLI）。 */
  private VideoTranscriptSettings videoTranscript = new VideoTranscriptSettings();
  /** 雷达文章正文获取（Jina / Trafilatura / Lightpanda 瀑布）。 */
  private ArticleContentSettings articleContent = new ArticleContentSettings();
  /** 本机 Trafilatura / Lightpanda 正文抽取 CLI。 */
  private ArticleExtractSettings articleExtract = new ArticleExtractSettings();
  /** 资料补充文章摘要：Playwright 分段截图 Vision 兜底（可选）。 */
  private PageScreenshotSettings pageScreenshot = new PageScreenshotSettings();
  /** 微信公众号开放平台 API。 */
  private WechatMpApiSettings wechatMp = new WechatMpApiSettings();
  /**
   * 密钥列 AES-GCM 主密钥（{@code *_ciphertext}）。
   * <p>开发写默认值；生产用环境变量 {@code CHENGWENFANG_SECRET_KEY} 覆盖。
   */
  private String secretKey = "chengwenfang-dev-secret-key-change-me!!";

  /** 文章正文瀑布配置。 */
  @Data
  public static class ArticleContentSettings {
    /** 是否启用 Jina Reader。 */
    private boolean jinaEnabled = true;
    /** Jina Reader 根地址。 */
    private String jinaBase = "https://r.jina.ai/";
    /** Jina 读超时秒数。 */
    private int jinaTimeoutSeconds = 20;
    /** Jina 连接超时秒数（连不上服务时尽快回退）。 */
    private int jinaConnectTimeoutSeconds = 5;
    /** 雷达获取阶段 URL 直连探测超时秒数。 */
    private int urlProbeTimeoutSeconds = 8;
    /** 是否启用 Trafilatura 补强。 */
    private boolean trafilaturaEnabled = true;
    /**
     * 是否启用 Lightpanda 浏览器渲染回退（Trafilatura 失败后）。
     * <p>需先 {@code docker compose --profile lightpanda up -d}。
     */
    private boolean browserEnabled = false;
    /** Lightpanda 单次渲染超时秒数。 */
    private int browserTimeoutSeconds = 45;
    /**
     * 浏览器调用模式：{@code docker-exec}（推荐 Windows）或 {@code binary}。
     * <p>词表见 crawler 模块 {@code ArticleBrowserModes}。
     */
    private String browserMode = "docker-exec";
    /** docker-exec 模式容器名（与 compose {@code container_name} 一致）。 */
    private String browserDockerContainer = "chengwenfang-lightpanda";
    /** binary 模式可执行文件路径或命令名。 */
    private String browserExecutable = "lightpanda";
    /**
     * 跳过远端抓取的主机后缀（直接走摘要）。
     * <p>仅由 {@code chengwenfang.article-content.skip-hosts} 配置注入，无代码兜底列表。
     * <p>浏览器回退<strong>不会</strong>绕过本列表（合规：不打验证墙）。
     */
    private List<String> skipHosts = new ArrayList<>();
    /** 同主机并发上限。 */
    private int perHostConcurrency = 2;
  }

  /** 知识库 RAG 开关。 */
  @Data
  public static class KnowledgeSettings {
    /** 图检索总开关。 */
    private GraphSettings graph = new GraphSettings();
    /** Agent 总开关与预算。 */
    private AgentSettings agent = new AgentSettings();
    /** LLM listwise rerank；可关以跑无 rerank 基线。 */
    private RerankSettings rerank = new RerankSettings();
    /** 会话记忆。 */
    private MemorySettings memory = new MemorySettings();
    /** 生成后忠实度校验。 */
    private FaithfulnessSettings faithfulness = new FaithfulnessSettings();
    /** 知识库 Prompt 版本。 */
    private PromptSettings prompt = new PromptSettings();
    /** 问答 Context Budget（与 Agent 轮次预算分离）。 */
    private ContextSettings context = new ContextSettings();
  }

  /** 问答上下文配额。 */
  @Data
  public static class ContextSettings {
    /** 四段切片总 token 软上限（chars≈token×4）。 */
    private int tokenBudget = 8000;
  }

  /** Graph feature flag。 */
  @Data
  public static class GraphSettings {
    /** 是否启用 Neo4j 图路径。 */
    private boolean enabled = false;
  }

  /** Agentic 配置。 */
  @Data
  public static class AgentSettings {
    /** 是否启用有界 Agent。 */
    private boolean enabled = false;
    /** 最大检索轮次。 */
    private int maxRounds = 3;
    /** token 预算（软上限，供观测与提前停）。 */
    private int tokenBudget = 8000;
  }

  /** Rerank 配置。 */
  @Data
  public static class RerankSettings {
    /** 默认开启 LLM listwise；可关。 */
    private boolean enabled = true;
  }

  /** 记忆配置。 */
  @Data
  public static class MemorySettings {
    /** 是否启用会话摘要记忆。 */
    private boolean enabled = true;
    /** 近重复合并相似度阈值（0–1）。 */
    private double mergeThreshold = 0.88;
    /** 是否从会话摘要晋升事实到长期记忆。 */
    private boolean promoteEnabled = true;
    /** 每 N 轮问答尝试一次摘要晋升。 */
    private int promoteEveryNTurns = 4;
  }

  /** 知识库 Prompt 版本配置。 */
  @Data
  public static class PromptSettings {
    /** 活跃版本号（如 v1）；改回旧版即回滚。 */
    private String activeVersion = "v1";
  }

  /** 答案忠实度二次校验（LLM）。 */
  @Data
  public static class FaithfulnessSettings {
    /** 默认开启；寒暄/拒答路径不调用。 */
    private boolean enabled = true;
  }

  /** Neo4j 连接。 */
  @Data
  public static class Neo4jSettings {
    /** Bolt URI。 */
    private String uri = "bolt://127.0.0.1:7687";
    /** 用户名。 */
    private String username = "neo4j";
    /** 密码。 */
    private String password = "chengwenfang_neo4j";
  }

  /**
   * 系统就绪探测地址。
   * <p>宿主机默认环回发布端口；应用容器内应改成 Compose 服务名。
   */
  @Data
  public static class ReadinessSettings {
    /** SearXNG 根地址（探测时追加 {@code /healthz}）。 */
    private String searxngUrl = "http://127.0.0.1:8080";
    /** Lightpanda 主机名。 */
    private String lightpandaHost = "127.0.0.1";
    /** Lightpanda 端口。 */
    private int lightpandaPort = 9222;
    /** Python CLI 依赖探测超时（秒）。 */
    private int cliProbeTimeoutSeconds = 8;
  }

  /** Qdrant 向量库配置（知识库 RAG）。 */
  @Data
  public static class QdrantSettings {
    /** 为 true 时必须连上 Qdrant；false 时使用内存实现且不计为就绪。 */
    private boolean enabled = true;
    /** Qdrant 主机名或 IP。 */
    private String host = "127.0.0.1";
    /** HTTP REST 端口（管理/健康检查）。 */
    private int httpPort = 6333;
    /** gRPC 端口（LangChain4j 嵌入存储客户端）。 */
    private int grpcPort = 6334;
    /** 集合名称。 */
    private String collection = "chengwenfang_knowledge";
    /** 是否使用 TLS 连接 gRPC。 */
    private boolean useTls = false;
    /** 可选 API Key（Qdrant Cloud 等）。 */
    private String apiKey = "";
  }

  /** 微信公众号 API 配置。 */
  @Data
  public static class WechatMpApiSettings {
    /** API 根地址；默认正式环境。 */
    private String apiBase = "https://api.weixin.qq.com";
  }

  /** Trafilatura / Lightpanda CLI 配置。 */
  @Data
  public static class ArticleExtractSettings {
    /** 是否启用 CLI。 */
    private boolean enabled = true;
    /** Python 可执行文件。 */
    private String pythonExecutable = "python";
    /** CLI 工作目录。 */
    private String workingDirectory = "scripts/article-extract";
    /** Trafilatura 单次超时秒数。 */
    private int stepTimeoutSeconds = 30;
  }

  /** 网页分段截图 CLI（Vision 兜底）。 */
  @Data
  public static class PageScreenshotSettings {
    /** 是否启用；默认关，需本机 Playwright + 多模态 AI。 */
    private boolean enabled = false;
    /** Python 可执行文件（建议 venv）。 */
    private String pythonExecutable = "python";
    /** CLI 工作目录。 */
    private String workingDirectory = "scripts/page-screenshot";
    /** 单次截图超时秒数。 */
    private int timeoutSeconds = 45;
    /** 最多截屏张数。 */
    private int maxShots = 3;
    /** 视口，如 1280x900。 */
    private String viewport = "1280x900";
  }

  /** 视频解析 CLI 配置。 */
  @Data
  public static class VideoTranscriptSettings {
    /** 是否启用解析能力。 */
    private boolean enabled = true;
    /** Python 可执行文件（可用 venv 路径）。 */
    private String pythonExecutable = "python";
    /** CLI 工作目录（相对仓库根或绝对路径）。 */
    private String workingDirectory = "scripts/video-transcript";
    /** 单步超时秒数。 */
    private int stepTimeoutSeconds = 900;
    /** 每选题单次 Job 最多解析条数。 */
    private int maxPerTopic = 20;
    /** 单次雷达采集 Job 内最多同步解析的视频条数。 */
    private int maxPerFetch = 10;
    /**
     * 允许解析的最大视频时长（分钟）；超过则在下载/ASR 前跳过。
     * {@code <= 0} 表示不限制。
     */
    private int maxDurationMinutes = 30;
    /** 画面解析最多关键帧数。 */
    private int visionMaxFrames = 12;
    /** ffmpeg 切镜 scene 阈值。 */
    private double sceneThreshold = 0.3;
    /** 密度下限：至少每 N 秒考虑一帧。 */
    private int fpsFloorSeconds = 8;
    /** 滑动去重：与近期帧像素差低于此百分比则丢弃。 */
    private int dedupDiffPercent = 8;
    /** 滑动去重窗口大小。 */
    private int dedupWindow = 4;
    /** 输出帧宽边上限（像素）。 */
    private int frameMaxWidth = 1024;
    /**
     * 本地工作目录产物保留天数（按目录内最新文件时间）；超时由启动与定时任务清理。
     * {@code <= 0} 时按 3 天处理。
     */
    private int workDirRetainDays = 3;
  }

  /** 成文坊线程池配置组。 */
  @Data
  public static class ExecutorSettings {
    /** 渠道抓取 fan-out / 多 query 并行池。 */
    private ChannelFetchExecutorSettings channelFetch = new ChannelFetchExecutorSettings();
    /** 雷达文章解析池（拉正文 + 事实速览，偏 I/O）。 */
    private RadarArticleEnrichExecutorSettings radarArticleEnrich =
        new RadarArticleEnrichExecutorSettings();
    /** 雷达视频解析池（字幕/ASR，偏 CPU，默认偏小以免拖死文章）。 */
    private RadarVideoEnrichExecutorSettings radarVideoEnrich =
        new RadarVideoEnrichExecutorSettings();
  }

  /** 渠道采集线程池参数。 */
  @Data
  public static class ChannelFetchExecutorSettings {
    /** 核心线程数。 */
    private int corePoolSize = 4;
    /** 最大线程数。 */
    private int maxPoolSize = 8;
    /** 有界队列容量。 */
    private int queueCapacity = 100;
    /** 空闲线程保活秒数。 */
    private int keepAliveSeconds = 60;
    /** 线程名前缀。 */
    private String threadNamePrefix = "channel-fetch-";
  }

  /** 雷达文章解析线程池参数（评分/筛选复用）。 */
  @Data
  public static class RadarArticleEnrichExecutorSettings {
    /** 核心线程数。 */
    private int corePoolSize = 15;
    /** 最大线程数。 */
    private int maxPoolSize = 15;
    /** 有界队列容量。 */
    private int queueCapacity = 64;
    /** 空闲线程保活秒数。 */
    private int keepAliveSeconds = 60;
    /** 线程名前缀。 */
    private String threadNamePrefix = "radar-article-";
  }

  /** 雷达视频解析线程池参数（Whisper/ffmpeg 重）。 */
  @Data
  public static class RadarVideoEnrichExecutorSettings {
    /** 核心线程数。 */
    private int corePoolSize = 5;
    /** 最大线程数。 */
    private int maxPoolSize = 5;
    /** 有界队列容量。 */
    private int queueCapacity = 32;
    /** 空闲线程保活秒数。 */
    private int keepAliveSeconds = 60;
    /** 线程名前缀。 */
    private String threadNamePrefix = "radar-video-";
  }
}
