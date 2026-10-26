package com.chengwenfang.skill.facade.enums;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 课程内容生成工作流节点类型（规范主流程 14 节点）。
 */
public enum CourseWorkflowNodeType {
  /** 加载已发布工作流快照与课程上下文。 */
  SNAPSHOT_CONTEXT("snapshot_context", "加载工作流与课程上下文", "none", true, "snapshot_context"),
  /** 原稿：开场与目标。 */
  DRAFT_OPENING("draft_opening", "原稿：开场与目标", "text", true, "draft_opening"),
  /** 原稿：核心原理。 */
  DRAFT_CORE("draft_core", "原稿：核心原理", "text", true, "draft_core"),
  /** 原稿：案例与推导。 */
  DRAFT_EXAMPLE("draft_example", "原稿：案例与推导", "text", true, "draft_example"),
  /** 原稿：本课小结。 */
  DRAFT_SUMMARY("draft_summary", "原稿：本课小结", "text", true, "draft_summary"),
  /** 原稿：下节课预告。 */
  DRAFT_PREVIEW("draft_preview", "原稿：下节课预告", "text", true, "draft_preview"),
  /** 校验原稿（内含必要时压缩）。 */
  REVIEW_DRAFT("review_draft", "校验原稿", "text", true, "review_draft"),
  /** 公众号等量转换。 */
  WECHAT_CONVERSION("wechat_conversion", "公众号等量转换", "text", true, "generate_wechat"),
  /** 课程封面生成。 */
  IMAGE_COVER("image_cover", "课程封面生成", "image", true, "image_cover"),
  /** 正文插图生成。 */
  IMAGE_INLINE("image_inline", "正文插图生成", "image", true, "image_inline"),
  /** 公式格式检查。 */
  FORMULA_STYLE_VALIDATION("formula_style_validation", "公式格式检查", "none", true, "formula_style_validation"),
  /** 公式可读性检查。 */
  FORMULA_EXPRESSION_VALIDATION(
      "formula_expression_validation", "公式可读性检查", "none", true, "formula_expression_validation"),
  /** 最终一致性检查。 */
  FINAL_VALIDATION("final_validation", "最终一致性检查", "none", true, "final_validation"),
  /** 保存课程版本。 */
  COMMIT_VERSION("commit_version", "保存课程版本", "none", true, "finalize");

  /** 节点 typeKey / key。 */
  private final String value;
  /** 设计器展示名。 */
  private final String displayName;
  /** 模型类型：text / image / none。 */
  private final String modelType;
  /** 是否属于当前内置主流程。 */
  private final boolean builtin;
  /** 前端进度步名（可与 typeKey 不同）。 */
  private final String progressStepKey;

  CourseWorkflowNodeType(
      String value, String displayName, String modelType, boolean builtin, String progressStepKey) {
    this.value = value;
    this.displayName = displayName;
    this.modelType = modelType;
    this.builtin = builtin;
    this.progressStepKey = progressStepKey;
  }

  /** @return 节点 typeKey */
  public String getValue() {
    return value;
  }

  /** @return 设计器展示名 */
  public String getDisplayName() {
    return displayName;
  }

  /** @return 模型类型 */
  public String getModelType() {
    return modelType;
  }

  /** @return 是否内置主流程节点 */
  public boolean isBuiltin() {
    return builtin;
  }

  /** @return 进度步名 */
  public String getProgressStepKey() {
    return progressStepKey;
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
  public static Optional<CourseWorkflowNodeType> from(String typeKey) {
    if (typeKey == null || typeKey.isBlank()) {
      return Optional.empty();
    }
    String normalized = typeKey.trim();
    return Arrays.stream(values())
        .filter(item -> item.value.equals(normalized))
        .findFirst();
  }

  /**
   * 是否课程规范节点 typeKey。
   *
   * @param typeKey 节点类型键
   * @return 可解析则为 true
   */
  public static boolean isCourseTypeKey(String typeKey) {
    return from(typeKey).isPresent();
  }

  /**
   * 是否草稿分段节点。
   *
   * @param typeKey 节点类型键
   * @return 匹配 draft_* 五段之一
   */
  public static boolean isDraftSection(String typeKey) {
    return DRAFT_OPENING.matches(typeKey)
        || DRAFT_CORE.matches(typeKey)
        || DRAFT_EXAMPLE.matches(typeKey)
        || DRAFT_SUMMARY.matches(typeKey)
        || DRAFT_PREVIEW.matches(typeKey);
  }

  /**
   * 是否配图节点。
   *
   * @param typeKey 节点类型键
   * @return 封面或正文插图
   */
  public static boolean isImageNode(String typeKey) {
    return IMAGE_COVER.matches(typeKey) || IMAGE_INLINE.matches(typeKey);
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
   * 是否校验类节点（公式或终检）。
   *
   * @param typeKey 节点类型键
   * @return 匹配校验节点
   */
  public static boolean isValidationNode(String typeKey) {
    return FORMULA_STYLE_VALIDATION.matches(typeKey)
        || FORMULA_EXPRESSION_VALIDATION.matches(typeKey)
        || FINAL_VALIDATION.matches(typeKey);
  }

  /**
   * 内置主流程节点（有序）。
   *
   * @return builtin 节点集合
   */
  public static Set<CourseWorkflowNodeType> builtinSet() {
    Set<CourseWorkflowNodeType> result = new LinkedHashSet<>();
    for (CourseWorkflowNodeType item : values()) {
      if (item.builtin) {
        result.add(item);
      }
    }
    return result;
  }

  /**
   * 内置主流程 typeKey 集合（有序）。
   *
   * @return typeKey 集合
   */
  public static Set<String> builtinTypeKeys() {
    Set<String> keys = new LinkedHashSet<>();
    for (CourseWorkflowNodeType item : builtinSet()) {
      keys.add(item.value);
    }
    return keys;
  }

  /**
   * 判断给定 typeKey 集合是否已覆盖全部内置主流程节点（可多不可少，允许 custom_ai 等扩展）。
   *
   * @param typeKeys 快照中收集到的 typeKey
   * @return 已覆盖全部 builtin 则为 true
   */
  public static boolean isFullCourseGraph(Set<String> typeKeys) {
    return typeKeys != null && typeKeys.containsAll(builtinTypeKeys());
  }
}
