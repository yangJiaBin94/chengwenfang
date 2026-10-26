package com.workbench.crawler.facade.constant;

/**
 * 文章正文获取默认值。
 */
public final class ArticleContentDefaults {
  private ArticleContentDefaults() {}

  /** 正文最短可用字符数。 */
  public static final int MIN_USABLE_CHARS = 80;
  /** 写入候选的正文最大字符数。 */
  public static final int MAX_TEXT_CHARS = 20000;
  /** Jina Reader 默认根地址。 */
  public static final String JINA_DEFAULT_BASE = "https://r.jina.ai/";
  /** Jina 默认超时秒（读超时）。 */
  public static final int JINA_DEFAULT_TIMEOUT_SECONDS = 20;
  /** Jina 连接超时秒（连不上 r.jina.ai 时尽快回退原生爬取）。 */
  public static final int JINA_DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;
  /** URL 直连探测默认超时秒。 */
  public static final int URL_PROBE_DEFAULT_TIMEOUT_SECONDS = 8;
  /** 同主机默认并发。 */
  public static final int DEFAULT_PER_HOST_CONCURRENCY = 2;
  /** Trafilatura CLI 默认超时秒。 */
  public static final int TRAFILATURA_DEFAULT_TIMEOUT_SECONDS = 30;
  /** Lightpanda 浏览器渲染默认超时秒。 */
  public static final int BROWSER_DEFAULT_TIMEOUT_SECONDS = 45;
  /** Lightpanda docker-exec 默认容器名。 */
  public static final String BROWSER_DEFAULT_DOCKER_CONTAINER = "workbench-lightpanda";
  /** Lightpanda 默认可执行文件名。 */
  public static final String BROWSER_DEFAULT_EXECUTABLE = "lightpanda";
  /** 浏览器形态 User-Agent。 */
  public static final String BROWSER_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
}
