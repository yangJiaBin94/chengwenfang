package com.workbench.skill.facade.constant;

/**
 * 热点成文内置 Skill 名称（与 seeder / 节点挂载一致）。
 */
public final class HotNewsSkillNames {
  private HotNewsSkillNames() {}

  /** 作者口吻与判断密度。 */
  public static final String AUTHOR_STYLE = "热点作者风格";

  /** 去 AI 套话。 */
  public static final String ANTI_AI = "热点去AI味";

  /**
   * 文章骨架阶段的配图规范：决定 illustrationStyle / coverBrief / imagePromptRequirements。
   */
  public static final String SKELETON_ILLUSTRATION = "热点文章骨架配图规范";

  /** 按骨架生成原稿。 */
  public static final String DRAFT_GENERATION = "热点资讯原稿生成";

  /** 原稿审校。 */
  public static final String DRAFT_REVIEW = "热点文章审校";

  /** 公众号转换。 */
  public static final String WECHAT_CONVERSION = "热点公众号转换";

  /** 配图生成与检查。 */
  public static final String IMAGE_GENERATION = "图片生成与检查";

  /**
   * 热点主题创建向导：根据主题说明总结元搜索短词。
   */
  public static final String THEME_SEARCH_QUERIES = "热点主题搜索词生成";

  /**
   * 热点主题创建向导：根据主题说明生成 GitHub Search 查询串。
   */
  public static final String THEME_GITHUB_QUERY = "热点主题GitHub查询串生成";

  /**
   * 来源管理：根据主题说明推荐可采集的 RSS / 网页 / 搜索渠道。
   */
  public static final String THEME_SOURCE_CHANNELS = "热点主题来源渠道推荐";
}
