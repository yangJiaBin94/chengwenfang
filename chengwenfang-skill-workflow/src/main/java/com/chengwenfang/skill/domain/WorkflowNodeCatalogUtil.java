package com.chengwenfang.skill.domain;

import com.chengwenfang.skill.facade.constant.WorkflowStructuralNodeKeys;
import com.chengwenfang.skill.facade.dto.view.WorkflowNodeTypeView;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 代码内置节点类型目录（课程/热点），含别名映射与副作用标记。 */
public final class WorkflowNodeCatalogUtil {
  private WorkflowNodeCatalogUtil() {}

  public static final Map<String, String> HOT_NEWS_LEGACY_KEY_MAP = Map.ofEntries(
      Map.entry("hot_news_context", "hot_news_load_skills"),
      Map.entry("hot_news_positioning", "hot_news_define_positioning"),
      Map.entry("hot_news_outline", "hot_news_plan_draft"),
      Map.entry("hot_news_draft_generation", "hot_news_generate_draft"),
      Map.entry("hot_news_draft_validation", "hot_news_review_draft"),
      Map.entry("hot_news_wechat_conversion", "hot_news_generate_wechat"),
      Map.entry("hot_news_article_validation", "hot_news_review_wechat"),
      Map.entry("hot_news_image_planning", "hot_news_plan_images"),
      Map.entry("hot_news_image_cover", "hot_news_generate_images"),
      Map.entry("hot_news_image_inline", "hot_news_generate_images"),
      Map.entry("hot_news_image_validation", "hot_news_validate_images"));

  public static String resolveTypeKey(String key) {
    if (key == null) {
      return null;
    }
    return HOT_NEWS_LEGACY_KEY_MAP.getOrDefault(key, key);
  }

  public static List<WorkflowNodeTypeView> registeredNodeTypes() {
    List<WorkflowNodeTypeView> types = new ArrayList<>();
    types.add(type("start", "Start", "控制", "none", "A", false, false, false));
    types.add(type("end", "End", "控制", "none", "A", false, false, false));
    types.add(type("llm_text", "LLM Text", "通用 LLM", "text", "B", true, false, false));
    types.add(type(
        CourseWorkflowNodeType.SNAPSHOT_CONTEXT.getValue(),
        CourseWorkflowNodeType.SNAPSHOT_CONTEXT.getDisplayName(),
        "上下文准备",
        CourseWorkflowNodeType.SNAPSHOT_CONTEXT.getModelType(),
        "A",
        true,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.DRAFT_OPENING.getValue(),
        CourseWorkflowNodeType.DRAFT_OPENING.getDisplayName(),
        "课程原稿",
        "text",
        "B",
        true,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.DRAFT_CORE.getValue(),
        CourseWorkflowNodeType.DRAFT_CORE.getDisplayName(),
        "课程原稿",
        "text",
        "B",
        true,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.DRAFT_EXAMPLE.getValue(),
        CourseWorkflowNodeType.DRAFT_EXAMPLE.getDisplayName(),
        "课程原稿",
        "text",
        "B",
        true,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.DRAFT_SUMMARY.getValue(),
        CourseWorkflowNodeType.DRAFT_SUMMARY.getDisplayName(),
        "课程原稿",
        "text",
        "B",
        true,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.DRAFT_PREVIEW.getValue(),
        CourseWorkflowNodeType.DRAFT_PREVIEW.getDisplayName(),
        "课程原稿",
        "text",
        "B",
        true,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.REVIEW_DRAFT.getValue(),
        CourseWorkflowNodeType.REVIEW_DRAFT.getDisplayName(),
        "原稿审核",
        CourseWorkflowNodeType.REVIEW_DRAFT.getModelType(),
        "B",
        true,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.WECHAT_CONVERSION.getValue(),
        CourseWorkflowNodeType.WECHAT_CONVERSION.getDisplayName(),
        "公众号文章",
        "text",
        "B",
        true,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.IMAGE_COVER.getValue(),
        CourseWorkflowNodeType.IMAGE_COVER.getDisplayName(),
        "课程图片",
        "image",
        "B",
        true,
        true,
        true));
    types.add(type(
        CourseWorkflowNodeType.IMAGE_INLINE.getValue(),
        CourseWorkflowNodeType.IMAGE_INLINE.getDisplayName(),
        "课程图片",
        "image",
        "B",
        true,
        true,
        true));
    types.add(type(
        CourseWorkflowNodeType.FORMULA_STYLE_VALIDATION.getValue(),
        CourseWorkflowNodeType.FORMULA_STYLE_VALIDATION.getDisplayName(),
        "质量检查",
        "none",
        "A",
        false,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.FORMULA_EXPRESSION_VALIDATION.getValue(),
        CourseWorkflowNodeType.FORMULA_EXPRESSION_VALIDATION.getDisplayName(),
        "质量检查",
        "none",
        "A",
        false,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.FINAL_VALIDATION.getValue(),
        CourseWorkflowNodeType.FINAL_VALIDATION.getDisplayName(),
        "质量检查",
        "none",
        "A",
        false,
        false,
        true));
    types.add(type(
        CourseWorkflowNodeType.COMMIT_VERSION.getValue(),
        CourseWorkflowNodeType.COMMIT_VERSION.getDisplayName(),
        "正式保存",
        "none",
        "A",
        true,
        true,
        true));
    types.add(type("controlled_condition", "条件判断", "流程控制", "none", "D", false, false, false));
    types.add(type("bounded_loop", "有界循环", "流程控制", "none", "D", false, false, false));
    types.add(type(
        WorkflowStructuralNodeKeys.CUSTOM_AI,
        "自定义节点",
        "自定义",
        "text",
        "B",
        true,
        false,
        false));
    // 热点主流程：仅注册当前 builtin 节点（废弃的定位/大纲规划不进目录）
    types.add(type(HotNewsWorkflowNodeType.LOAD_CONTEXT.getValue(), "读取文章骨架与资料", "上下文准备", "none", "A", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.LOAD_SKILLS.getValue(), "加载热点文章 Skill", "上下文准备", "text", "B", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.PREPARE_FACTS.getValue(), "整理资料线索为事实包", "事实准备", "text", "B", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.GENERATE_DRAFT.getValue(), "按骨架生成热点原稿", "热点原稿", "text", "B", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.REVIEW_DRAFT.getValue(), "审校热点原稿", "原稿审核", "text", "B", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.GENERATE_WECHAT.getValue(), "生成公众号文章", "公众号文章", "text", "B", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.REVIEW_WECHAT.getValue(), "审校公众号文章", "公众号审校", "text", "B", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.PLAN_IMAGES.getValue(), "确认配套图片计划", "配套图片", "none", "A", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.GENERATE_IMAGES.getValue(), "生成配套图片", "配套图片", "image", "B", true, true, false));
    types.add(type(HotNewsWorkflowNodeType.VALIDATE_IMAGES.getValue(), "检查图片数量与引用", "图片审核", "none", "A", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.FINAL_VALIDATION.getValue(), "最终完整性检查", "质量检查", "none", "A", true, false, false));
    types.add(type(HotNewsWorkflowNodeType.COMMIT_VERSION.getValue(), "保存热点文章版本", "正式保存", "none", "A", true, true, false));
    // 热点雷达采集主流程
    for (HotNewsRadarCollectNodeType radarNode : HotNewsRadarCollectNodeType.builtinSet()) {
      boolean textNode = "text".equals(radarNode.getModelType());
      boolean sideEffects = HotNewsRadarCollectNodeType.PERSIST_CLUSTER.matches(radarNode.getValue())
          || HotNewsRadarCollectNodeType.SCORE_TOP10.matches(radarNode.getValue())
          || HotNewsRadarCollectNodeType.FINALIZE.matches(radarNode.getValue());
      types.add(type(
          radarNode.getValue(),
          radarNode.getDisplayName(),
          "雷达采集",
          radarNode.getModelType(),
          textNode ? "B" : "A",
          true,
          sideEffects,
          false));
    }
    return types;
  }

  public static WorkflowNodeTypeView type(
      String key,
      String name,
      String category,
      String modelType,
      String configurability,
      boolean supportsRetry,
      boolean sideEffects,
      boolean includeInBaseline) {
    Map<String, Object> configSchema = new LinkedHashMap<>();
    configSchema.put("type", "object");
    configSchema.put("properties", Map.of(
        "timeoutSeconds", Map.of("type", "integer", "minimum", 10, "maximum", 900),
        "maxAttempts", Map.of("type", "integer", "minimum", 1, "maximum", 5),
        "failurePolicy", Map.of(
            "type", "string",
            "enum", List.of("stop", "retry_then_stop", "continue_with_warning"))
    ));
    configSchema.put("required", List.of("timeoutSeconds", "maxAttempts", "failurePolicy"));
    configSchema.put("additionalProperties", true);

    return WorkflowNodeTypeView.builder()
        .key(key)
        .name(name)
        .description("由工作台内置执行器完成" + name)
        .category(category)
        .modelType(modelType)
        .executorVersion("1.0")
        .configurability(configurability)
        .supportsRetry(supportsRetry)
        .sideEffects(sideEffects)
        .includeInBaseline(includeInBaseline)
        .configSchema(configSchema)
        .inputSchema(Map.of("type", "object"))
        .outputSchema(Map.of("type", "object"))
        .build();
  }

  public static boolean hasSideEffects(String typeKey) {
    String key = resolveTypeKey(typeKey);
    return registeredNodeTypes().stream()
        .anyMatch(type -> key.equals(type.getKey()) && Boolean.TRUE.equals(type.getSideEffects()));
  }

  /**
   * 节点 key → 展示名；未知 key 原样返回。
   *
   * @param nodeKey 节点 key 或 typeKey
   * @return 目录中的中文名，或原 key
   */
  public static String nodeDisplayName(String nodeKey) {
    if (nodeKey == null || nodeKey.isBlank()) {
      return "";
    }
    String resolved = resolveTypeKey(nodeKey);
    return registeredNodeTypes().stream()
        .filter(type -> resolved.equals(type.getKey()) || nodeKey.equals(type.getKey()))
        .map(WorkflowNodeTypeView::getName)
        .findFirst()
        .orElse(nodeKey);
  }
}
