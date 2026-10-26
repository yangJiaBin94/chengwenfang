package com.workbench.skill.facade.enums;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 热点雷达采集工作流节点类型。
 */
public enum HotNewsRadarCollectNodeType {
  /** 读取主题、渠道与采集配额。 */
  LOAD_CONTEXT("radar_load_context", "读取采集上下文", "none", true),
  /** 加载采集 Skill 快照。 */
  LOAD_SKILLS("radar_load_skills", "加载采集 Skill", "text", true),
  /** 并行渠道拉取到内存候选。 */
  FETCH_CHANNELS("radar_fetch_channels", "获取渠道资源", "none", true),
  /** 打标、解析正文/视频并生成事实速览。 */
  ENRICH_SUMMARIZE("radar_enrich_summarize", "解析与事实速览", "text", true),
  /**
   * 素材筛选（主题相关、营销等可扩展过滤），弃用只打日志。
   * <p>历史 typeKey {@code radar_marketing_filter} 仍可经 {@link #from(String)} 解析到本枚举。
   */
  CANDIDATE_FILTER("radar_candidate_filter", "素材筛选", "text", true),
  /** 幸存者落库、成簇、合并簇摘要。 */
  PERSIST_CLUSTER("radar_persist_cluster", "落库与成簇", "text", true),
  /** 按主题问卷生成 AI 主题种子素材；本批不足限额时补齐（不走渠道抓取）。 */
  AI_THEME_SEED("radar_ai_theme_seed", "AI 主题素材", "text", true),
  /** 评分并标记 Top10。 */
  SCORE_TOP10("radar_score_top10", "评分取 Top10", "none", true),
  /** 关闭采集 run 并汇总输出。 */
  FINALIZE("radar_finalize", "完成采集收尾", "none", true);

  /** 节点 typeKey / key。 */
  private final String value;
  /** 展示名。 */
  private final String displayName;
  /** 模型类型：none / text。 */
  private final String modelType;
  /** 是否属于内置主流程。 */
  private final boolean builtin;

  HotNewsRadarCollectNodeType(
      String value, String displayName, String modelType, boolean builtin) {
    this.value = value;
    this.displayName = displayName;
    this.modelType = modelType;
    this.builtin = builtin;
  }

  /**
   * @return 节点 typeKey
   */
  public String getValue() {
    return value;
  }

  /**
   * @return 展示名
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * @return 模型类型
   */
  public String getModelType() {
    return modelType;
  }

  /**
   * @return 是否内置主流程节点
   */
  public boolean isBuiltin() {
    return builtin;
  }

  /**
   * 是否与给定 typeKey 相等。
   *
   * @param typeKey 节点类型键
   * @return 匹配则为 true
   */
  public boolean matches(String typeKey) {
    return typeKey != null && value.equals(typeKey.trim());
  }

  /**
   * 解析 typeKey；未知则 empty。
   *
   * @param typeKey 节点类型键
   * @return 枚举可选值
   */
  public static Optional<HotNewsRadarCollectNodeType> from(String typeKey) {
    if (typeKey == null || typeKey.isBlank()) {
      return Optional.empty();
    }
    String normalized = typeKey.trim();
    // 兼容旧版「营销筛选」节点键
    if ("radar_marketing_filter".equals(normalized)) {
      return Optional.of(CANDIDATE_FILTER);
    }
    return Arrays.stream(values())
        .filter(item -> item.value.equals(normalized))
        .findFirst();
  }

  /**
   * 内置主流程节点集合（有序）。
   *
   * @return builtin 节点
   */
  public static Set<HotNewsRadarCollectNodeType> builtinSet() {
    Set<HotNewsRadarCollectNodeType> result = new LinkedHashSet<>();
    for (HotNewsRadarCollectNodeType item : values()) {
      if (item.builtin) {
        result.add(item);
      }
    }
    return result;
  }

  /**
   * 内置主流程 typeKey 集合。
   *
   * @return typeKey 集合
   */
  public static Set<String> builtinTypeKeys() {
    Set<String> keys = new LinkedHashSet<>();
    for (HotNewsRadarCollectNodeType item : builtinSet()) {
      keys.add(item.value);
    }
    return keys;
  }

  /**
   * 是否雷达采集节点 typeKey。
   *
   * @param typeKey 节点类型键
   * @return 以 radar_ 开头则为 true
   */
  public static boolean isRadarTypeKey(String typeKey) {
    return typeKey != null && typeKey.toLowerCase(Locale.ROOT).startsWith("radar_");
  }
}
