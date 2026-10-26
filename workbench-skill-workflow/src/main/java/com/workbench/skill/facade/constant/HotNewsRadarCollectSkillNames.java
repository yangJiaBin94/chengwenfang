package com.workbench.skill.facade.constant;

/**
 * 热点雷达采集内置 Skill 名称（与 seeder / 节点挂载一致）。
 */
public final class HotNewsRadarCollectSkillNames {
  private HotNewsRadarCollectSkillNames() {}

  /** 文章类素材事实速览。 */
  public static final String ARTICLE_FACT_BRIEF = "热点文章事实速览";

  /** 从页面文本中抽取可能的发布/更新日期。 */
  public static final String ARTICLE_PUBLISH_DATE = "热点文章发布日抽取";

  /** 视频类素材事实速览。 */
  public static final String VIDEO_FACT_BRIEF = "热点视频事实速览";

  /** 营销/硬广判定。 */
  public static final String MARKETING_FILTER = "热点素材营销判定";

  /** 选题簇摘要合并。 */
  public static final String CLUSTER_SUMMARY_MERGE = "热点选题簇摘要合并";

  /** 按主题问卷生成若干条 AI 主题种子素材（条数由用户提示指定）。 */
  public static final String AI_THEME_SEED = "热点 AI 主题素材";
}
