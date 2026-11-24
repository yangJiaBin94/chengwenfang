package com.chengwenfang.app.bootstrap;

/**
 * 系统就绪检查项稳定键，供面板与测试引用。
 */
public final class SystemReadinessKeys {
  /** 内置提示词包。 */
  public static final String PROMPT_PACKS = "prompt_packs";
  /** 三条主工作流。 */
  public static final String WORKFLOWS = "workflows";
  /** Qdrant 真连接。 */
  public static final String QDRANT = "qdrant";
  /** 生成用多模态 Key。 */
  public static final String GENERATION_KEY = "generation_key";
  /** 嵌入 Key。 */
  public static final String EMBEDDING_KEY = "embedding_key";
  /** 图像生成 Key。 */
  public static final String IMAGE_KEY = "image_key";
  /** 正文抽取 CLI。 */
  public static final String CLI_ARTICLE_EXTRACT = "cli_article_extract";
  /** 视频转写 CLI。 */
  public static final String CLI_VIDEO_TRANSCRIPT = "cli_video_transcript";
  /** 页面截图 CLI。 */
  public static final String CLI_PAGE_SCREENSHOT = "cli_page_screenshot";
  /** SearXNG。 */
  public static final String SEARXNG = "searxng";
  /** Lightpanda。 */
  public static final String LIGHTPANDA = "lightpanda";
  /** 知识库 Graph。 */
  public static final String GRAPH = "graph";
  /** 知识库 Agent。 */
  public static final String AGENT = "agent";

  private SystemReadinessKeys() {}

  /**
   * 内置种子项的问题文案已由体检门面给出，面板不再重复追加。
   *
   * @param key 检查项键
   * @return 属于内置种子项则为 true
   */
  public static boolean isBuiltinSeed(String key) {
    return PROMPT_PACKS.equals(key) || WORKFLOWS.equals(key);
  }
}
