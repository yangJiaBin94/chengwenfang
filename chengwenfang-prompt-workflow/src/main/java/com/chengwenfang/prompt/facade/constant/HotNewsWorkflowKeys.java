package com.chengwenfang.prompt.facade.constant;

/**
 * 热点成文内置工作流标识常量（key / 展示名 / scene）。
 */
public final class HotNewsWorkflowKeys {
  private HotNewsWorkflowKeys() {}

  /** 工作流定义唯一 key。 */
  public static final String DEFINITION_KEY = "hot-news-article-generation";

  /** 工作流展示名。 */
  public static final String DISPLAY_NAME = "生成热点公众号文章";

  /** 与 JobTypes / PromptPack.scene 对齐的场景码。 */
  public static final String SCENE = "hot_news_article_generation";

  /** 内置发布说明。 */
  public static final String BUILTIN_RELEASE_NOTES = "热点公众号文章：骨架优先主流程 + 成文佐证截屏";
}
