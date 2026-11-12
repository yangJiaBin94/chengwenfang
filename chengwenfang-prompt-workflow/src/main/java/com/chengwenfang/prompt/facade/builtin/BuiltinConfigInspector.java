package com.chengwenfang.prompt.facade.builtin;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.prompt.facade.constant.CoursePromptPackNames;
import com.chengwenfang.prompt.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.prompt.facade.constant.HotNewsRadarCollectPromptPackNames;
import com.chengwenfang.prompt.facade.constant.HotNewsRadarCollectWorkflowKeys;
import com.chengwenfang.prompt.facade.constant.HotNewsPromptPackNames;
import com.chengwenfang.prompt.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.prompt.facade.constant.PromptPackSceneKeys;
import com.chengwenfang.prompt.facade.constant.WorkflowGraphMapKeys;
import com.chengwenfang.prompt.facade.enums.CourseWorkflowNodeType;
import com.chengwenfang.prompt.facade.enums.HotNewsRadarCollectNodeType;
import com.chengwenfang.prompt.facade.enums.HotNewsWorkflowNodeType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 只读检查内置提示词包 与三条主工作流是否已在库中就绪（门面层，供 app / 启动体检共用）。
 */
public final class BuiltinConfigInspector {
  /** 首次部署脚本提示（日志与 HTTP 面板共用，不写库）。 */
  public static final String BOOTSTRAP_HINT =
      "powershell -ExecutionPolicy Bypass -File .\\scripts\\bootstrap\\Initialize-ChengwenfangSeed.ps1";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private BuiltinConfigInspector() {}

  /**
   * 一条已入库 提示词包 的体检输入。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class PromptPackRow {
    /** 主键。 */
    private final String id;
    /** 名称。 */
    private final String name;
    /** 场景。 */
    private final String scene;
    /** 是否启用。 */
    private final boolean enabled;
    /** 系统指令。 */
    private final String systemInstructions;
  }

  /**
   * 一条工作流定义及其已发布快照。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class WorkflowRow {
    /** 定义 key。 */
    private final String key;
    /** 已发布版本 id，可空。 */
    private final String publishedVersionId;
    /** 已发布快照 JSON，可空。 */
    private final String snapshotJson;
  }

  /**
   * 汇总缺失项。空列表表示通过。
   *
   * @param promptPacks    库内 提示词包
   * @param workflows 库内工作流（可只含内置 key）
   * @return 结构化问题列表
   */
  public static List<BuiltinIssue> inspect(List<PromptPackRow> promptPacks, List<WorkflowRow> workflows) {
    List<PromptPackRow> rows = promptPacks == null ? List.of() : promptPacks;
    List<WorkflowRow> graphs = workflows == null ? List.of() : workflows;
    Set<String> promptPackIds = new HashSet<>();
    for (PromptPackRow row : rows) {
      if (row != null && StrUtil.isNotBlank(row.id())) {
        promptPackIds.add(row.id());
      }
    }
    List<BuiltinIssue> issues = new ArrayList<>();
    for (ExpectedPromptPack expected : expectedPromptPacks()) {
      PromptPackRow matched = findPromptPack(rows, expected.name(), expected.scene());
      if (matched == null) {
        issues.add(new BuiltinIssue(
            BuiltinIssueKind.PROMPT_PACK,
            "缺少 提示词包 name=" + expected.name() + " scene=" + expected.scene()));
        continue;
      }
      if (!matched.enabled() || StrUtil.isBlank(matched.systemInstructions())) {
        issues.add(new BuiltinIssue(
            BuiltinIssueKind.PROMPT_PACK,
            "提示词包 不可用 name=" + expected.name() + " scene=" + expected.scene()
                + " reason=未启用或系统指令为空"));
      }
    }
    inspectWorkflow(
        issues, graphs, promptPackIds, CourseWorkflowKeys.DEFINITION_KEY, CourseWorkflowNodeType.builtinTypeKeys());
    inspectWorkflow(
        issues, graphs, promptPackIds, HotNewsWorkflowKeys.DEFINITION_KEY, HotNewsWorkflowNodeType.builtinTypeKeys());
    inspectWorkflow(
        issues, graphs, promptPackIds, HotNewsRadarCollectWorkflowKeys.DEFINITION_KEY,
        HotNewsRadarCollectNodeType.builtinTypeKeys());
    return issues;
  }

  /**
   * 将问题列表转为纯文案（HTTP / 日志）。
   *
   * @param issues 结构化问题
   * @return 文案列表
   */
  public static List<String> messageList(List<BuiltinIssue> issues) {
    if (issues == null || issues.isEmpty()) {
      return List.of();
    }
    List<String> messages = new ArrayList<>(issues.size());
    for (BuiltinIssue issue : issues) {
      if (issue != null && StrUtil.isNotBlank(issue.message())) {
        messages.add(issue.message());
      }
    }
    return List.copyOf(messages);
  }

  /**
   * 是否存在工作流域问题。
   *
   * @param issues 结构化问题
   * @return true 表示工作流未就绪
   */
  public static boolean hasWorkflowIssue(List<BuiltinIssue> issues) {
    if (issues == null) {
      return false;
    }
    for (BuiltinIssue issue : issues) {
      if (issue != null && issue.isWorkflow()) {
        return true;
      }
    }
    return false;
  }

  /**
   * 内置提示词包 期望清单（名称 + 场景）。
   *
   * @return 期望列表
   */
  public static List<ExpectedPromptPack> expectedPromptPacks() {
    List<ExpectedPromptPack> expected = new ArrayList<>();
    expected.add(new ExpectedPromptPack(CoursePromptPackNames.CHENGWENFANG_RULES, PromptPackSceneKeys.GLOBAL));
    expected.add(new ExpectedPromptPack(CoursePromptPackNames.COURSE_REVIEW, PromptPackSceneKeys.GLOBAL));
    expected.add(new ExpectedPromptPack(CoursePromptPackNames.IMAGE_GENERATION, PromptPackSceneKeys.GLOBAL));
    expected.add(new ExpectedPromptPack(CoursePromptPackNames.COURSE_DRAFT, CourseWorkflowKeys.SCENE));
    expected.add(new ExpectedPromptPack(CoursePromptPackNames.WECHAT_CONVERSION, CourseWorkflowKeys.SCENE));
    expected.add(new ExpectedPromptPack(CoursePromptPackNames.IMAGE_PLANNING, CourseWorkflowKeys.SCENE));
    expected.add(new ExpectedPromptPack(CoursePromptPackNames.FINALIZATION, CourseWorkflowKeys.SCENE));
    expected.add(new ExpectedPromptPack(CoursePromptPackNames.AUTHOR_STYLE, CourseWorkflowKeys.SCENE));
    String hot = HotNewsWorkflowKeys.SCENE;
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.AUTHOR_STYLE, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.ANTI_AI, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.SKELETON_ILLUSTRATION, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.DRAFT_GENERATION, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.DRAFT_REVIEW, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.WECHAT_CONVERSION, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.IMAGE_GENERATION, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.THEME_SEARCH_QUERIES, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.THEME_GITHUB_QUERY, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.THEME_SOURCE_CHANNELS, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.THEME_CORE_INTENT, hot));
    expected.add(new ExpectedPromptPack(HotNewsPromptPackNames.THEME_COLLECT_STRATEGY, hot));
    String radar = HotNewsRadarCollectWorkflowKeys.SCENE;
    expected.add(new ExpectedPromptPack(HotNewsRadarCollectPromptPackNames.ARTICLE_FACT_BRIEF, radar));
    expected.add(new ExpectedPromptPack(HotNewsRadarCollectPromptPackNames.ARTICLE_PUBLISH_DATE, radar));
    expected.add(new ExpectedPromptPack(HotNewsRadarCollectPromptPackNames.VIDEO_FACT_BRIEF, radar));
    expected.add(new ExpectedPromptPack(HotNewsRadarCollectPromptPackNames.MARKETING_FILTER, radar));
    expected.add(new ExpectedPromptPack(HotNewsRadarCollectPromptPackNames.CLUSTER_SUMMARY_MERGE, radar));
    expected.add(new ExpectedPromptPack(HotNewsRadarCollectPromptPackNames.AI_THEME_SEED, radar));
    return List.copyOf(expected);
  }

  private static void inspectWorkflow(
      List<BuiltinIssue> issues,
      List<WorkflowRow> graphs,
      Set<String> promptPackIds,
      String key,
      Set<String> requiredTypeKeys) {
    WorkflowRow row = null;
    for (WorkflowRow candidate : graphs) {
      if (candidate != null && key.equals(candidate.key())) {
        row = candidate;
        break;
      }
    }
    if (row == null || StrUtil.isBlank(row.publishedVersionId()) || StrUtil.isBlank(row.snapshotJson())) {
      issues.add(new BuiltinIssue(BuiltinIssueKind.WORKFLOW, "缺少已发布工作流 key=" + key));
      return;
    }
    Map<String, Object> snapshot;
    try {
      snapshot = MAPPER.readValue(row.snapshotJson(), new TypeReference<>() {});
    } catch (Exception exception) {
      issues.add(new BuiltinIssue(BuiltinIssueKind.WORKFLOW, "工作流快照无法解析 key=" + key));
      return;
    }
    Set<String> typeKeys = new LinkedHashSet<>();
    Object nodesRaw = snapshot.get(WorkflowGraphMapKeys.NODES);
    if (nodesRaw instanceof List<?> nodes) {
      for (Object nodeObj : nodes) {
        if (!(nodeObj instanceof Map<?, ?> node)) {
          continue;
        }
        Object typeKey = node.get(WorkflowGraphMapKeys.TYPE_KEY);
        if (typeKey == null) {
          typeKey = node.get(WorkflowGraphMapKeys.TYPE);
        }
        if (typeKey != null && StrUtil.isNotBlank(String.valueOf(typeKey))) {
          typeKeys.add(String.valueOf(typeKey));
        }
        Object bindingsRaw = node.get(WorkflowGraphMapKeys.PROMPT_PACK_BINDINGS);
        if (!(bindingsRaw instanceof List<?> bindings)) {
          continue;
        }
        for (Object bindingObj : bindings) {
          if (!(bindingObj instanceof Map<?, ?> binding)) {
            continue;
          }
          Object promptPackId = binding.get(WorkflowGraphMapKeys.PROMPT_PACK_ID);
          if (promptPackId == null || StrUtil.isBlank(String.valueOf(promptPackId))) {
            issues.add(new BuiltinIssue(BuiltinIssueKind.WORKFLOW, "工作流节点 提示词包绑定为空 key=" + key));
            continue;
          }
          if (!promptPackIds.contains(String.valueOf(promptPackId))) {
            issues.add(new BuiltinIssue(
                BuiltinIssueKind.WORKFLOW,
                "工作流绑定了不存在的 提示词包 key=" + key + " promptPackId=" + promptPackId));
          }
        }
      }
    }
    if (!typeKeys.containsAll(requiredTypeKeys)) {
      Set<String> missing = new LinkedHashSet<>(requiredTypeKeys);
      missing.removeAll(typeKeys);
      issues.add(new BuiltinIssue(
          BuiltinIssueKind.WORKFLOW,
          "工作流节点不完整 key=" + key + " missing=" + missing));
    }
  }

  private static PromptPackRow findPromptPack(List<PromptPackRow> rows, String name, String scene) {
    for (PromptPackRow row : rows) {
      if (row == null) {
        continue;
      }
      if (name.equals(row.name()) && scene.equals(StrUtil.nullToEmpty(row.scene()))) {
        return row;
      }
    }
    return null;
  }

  /**
   * 期望的内置提示词包。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class ExpectedPromptPack {
    /** 名称。 */
    private final String name;
    /** 场景。 */
    private final String scene;
  }
}
