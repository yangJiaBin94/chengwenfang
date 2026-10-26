package com.chengwenfang.skill.facade.enums;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 热点成文工作流节点类型。
 * <p>内置主流程以「文章骨架」为结构来源，不再单独做定位/大纲规划节点。
 */
public enum HotNewsWorkflowNodeType {
  /** 读取文章骨架、选题与资料线索。 */
  LOAD_CONTEXT("hot_news_load_context", true),
  /** 加载热点文章 Skill 快照。 */
  LOAD_SKILLS("hot_news_load_skills", true),
  /** 整理资料线索为事实包（辅助骨架，不替代结构）。 */
  PREPARE_FACTS("hot_news_prepare_facts", true),
  /** 已废弃：定位由骨架 coreThesis/readerSignals 承担。 */
  DEFINE_POSITIONING("hot_news_define_positioning", false),
  /** 已废弃：结构由骨架 sectionPlan/narrativeShape 承担。 */
  PLAN_DRAFT("hot_news_plan_draft", false),
  /** 按骨架生成原稿。 */
  GENERATE_DRAFT("hot_news_generate_draft", true),
  /** 审校原稿（含字数与结构）。 */
  REVIEW_DRAFT("hot_news_review_draft", true),
  /** 生成公众号正文。 */
  GENERATE_WECHAT("hot_news_generate_wechat", true),
  /** 审校公众号正文。 */
  REVIEW_WECHAT("hot_news_review_wechat", true),
  /** 按骨架与插图档确认配图计划。 */
  PLAN_IMAGES("hot_news_plan_images", true),
  /** 生成配图。 */
  GENERATE_IMAGES("hot_news_generate_images", true),
  /** 校验配图数量与引用。 */
  VALIDATE_IMAGES("hot_news_validate_images", true),
  /** 最终完整性检查（字数/配图/审校门禁）。 */
  FINAL_VALIDATION("hot_news_final_validation", true),
  /** 提交文章版本。 */
  COMMIT_VERSION("hot_news_commit_version", true);

  /** 节点 typeKey / key。 */
  private final String value;
  /** 是否属于当前内置主流程。 */
  private final boolean builtin;

  HotNewsWorkflowNodeType(String value, boolean builtin) {
    this.value = value;
    this.builtin = builtin;
  }

  /** @return 节点 typeKey */
  public String getValue() {
    return value;
  }

  /** @return 是否属于内置主流程 */
  public boolean isBuiltin() {
    return builtin;
  }

  /**
   * 兼容旧调用。
   *
   * @return 与 {@link #isBuiltin()} 相同
   */
  public boolean isLean() {
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
  public static Optional<HotNewsWorkflowNodeType> from(String typeKey) {
    if (typeKey == null || typeKey.isBlank()) {
      return Optional.empty();
    }
    String normalized = typeKey.trim();
    return Arrays.stream(values())
        .filter(item -> item.value.equals(normalized))
        .findFirst();
  }

  /**
   * 内置主流程节点集合（有序）。
   *
   * @return builtin 节点
   */
  public static Set<HotNewsWorkflowNodeType> builtinSet() {
    Set<HotNewsWorkflowNodeType> result = new LinkedHashSet<>();
    for (HotNewsWorkflowNodeType item : values()) {
      if (item.builtin) {
        result.add(item);
      }
    }
    return result;
  }

  /**
   * 兼容旧调用。
   *
   * @return 内置主流程节点
   */
  public static Set<HotNewsWorkflowNodeType> leanSet() {
    return builtinSet();
  }

  /**
   * 内置主流程 typeKey 集合。
   *
   * @return typeKey 集合
   */
  public static Set<String> builtinTypeKeys() {
    Set<String> keys = new LinkedHashSet<>();
    for (HotNewsWorkflowNodeType item : builtinSet()) {
      keys.add(item.value);
    }
    return keys;
  }

  /**
   * 兼容旧调用。
   *
   * @return typeKey 集合
   */
  public static Set<String> leanTypeKeys() {
    return builtinTypeKeys();
  }

  /**
   * 是否热点节点 typeKey。
   *
   * @param typeKey 节点类型键
   * @return 以 hot_news_ 开头则为 true
   */
  public static boolean isHotNewsTypeKey(String typeKey) {
    return typeKey != null && typeKey.toLowerCase(Locale.ROOT).startsWith("hot_news_");
  }

  /**
   * 是否提交版本节点。
   *
   * @param typeKey 节点类型键
   * @return 匹配 COMMIT_VERSION
   */
  public static boolean isCommitVersion(String typeKey) {
    return COMMIT_VERSION.matches(typeKey);
  }

  /**
   * 是否出图节点。
   *
   * @param typeKey 节点类型键
   * @return 匹配 GENERATE_IMAGES
   */
  public static boolean isGenerateImages(String typeKey) {
    return GENERATE_IMAGES.matches(typeKey);
  }

  /**
   * 是否最终完整性检查节点。
   *
   * @param typeKey 节点类型键
   * @return 匹配 FINAL_VALIDATION
   */
  public static boolean isFinalValidation(String typeKey) {
    return FINAL_VALIDATION.matches(typeKey);
  }
}
