package com.workbench.skill.facade.builtin;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.skill.facade.constant.CourseSkillNames;
import com.workbench.skill.facade.constant.CourseWorkflowKeys;
import com.workbench.skill.facade.constant.HotNewsRadarCollectSkillNames;
import com.workbench.skill.facade.constant.HotNewsRadarCollectWorkflowKeys;
import com.workbench.skill.facade.constant.HotNewsSkillNames;
import com.workbench.skill.facade.constant.HotNewsWorkflowKeys;
import com.workbench.skill.facade.constant.SkillSceneKeys;
import com.workbench.skill.facade.constant.WorkflowGraphMapKeys;
import com.workbench.skill.facade.enums.CourseWorkflowNodeType;
import com.workbench.skill.facade.enums.HotNewsRadarCollectNodeType;
import com.workbench.skill.facade.enums.HotNewsWorkflowNodeType;
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
 * 只读检查内置 Skill 与三条主工作流是否已在库中就绪（门面层，供 app / 启动体检共用）。
 */
public final class BuiltinConfigInspector {
  /** 首次部署脚本提示（日志与 HTTP 面板共用，不写库）。 */
  public static final String BOOTSTRAP_HINT =
      "powershell -ExecutionPolicy Bypass -File .\\scripts\\bootstrap\\Initialize-WorkbenchSeed.ps1";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private BuiltinConfigInspector() {}

  /**
   * 一条已入库 Skill 的体检输入。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class SkillRow {
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
   * @param skills    库内 Skill
   * @param workflows 库内工作流（可只含内置 key）
   * @return 结构化问题列表
   */
  public static List<BuiltinIssue> inspect(List<SkillRow> skills, List<WorkflowRow> workflows) {
    List<SkillRow> rows = skills == null ? List.of() : skills;
    List<WorkflowRow> graphs = workflows == null ? List.of() : workflows;
    Set<String> skillIds = new HashSet<>();
    for (SkillRow row : rows) {
      if (row != null && StrUtil.isNotBlank(row.id())) {
        skillIds.add(row.id());
      }
    }
    List<BuiltinIssue> issues = new ArrayList<>();
    for (ExpectedSkill expected : expectedSkills()) {
      SkillRow matched = findSkill(rows, expected.name(), expected.scene());
      if (matched == null) {
        issues.add(new BuiltinIssue(
            BuiltinIssueKind.SKILL,
            "缺少 Skill name=" + expected.name() + " scene=" + expected.scene()));
        continue;
      }
      if (!matched.enabled() || StrUtil.isBlank(matched.systemInstructions())) {
        issues.add(new BuiltinIssue(
            BuiltinIssueKind.SKILL,
            "Skill 不可用 name=" + expected.name() + " scene=" + expected.scene()
                + " reason=未启用或系统指令为空"));
      }
    }
    inspectWorkflow(
        issues, graphs, skillIds, CourseWorkflowKeys.DEFINITION_KEY, CourseWorkflowNodeType.builtinTypeKeys());
    inspectWorkflow(
        issues, graphs, skillIds, HotNewsWorkflowKeys.DEFINITION_KEY, HotNewsWorkflowNodeType.builtinTypeKeys());
    inspectWorkflow(
        issues, graphs, skillIds, HotNewsRadarCollectWorkflowKeys.DEFINITION_KEY,
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
   * 内置 Skill 期望清单（名称 + 场景）。
   *
   * @return 期望列表
   */
  public static List<ExpectedSkill> expectedSkills() {
    List<ExpectedSkill> expected = new ArrayList<>();
    expected.add(new ExpectedSkill(CourseSkillNames.WORKBENCH_RULES, SkillSceneKeys.GLOBAL));
    expected.add(new ExpectedSkill(CourseSkillNames.COURSE_REVIEW, SkillSceneKeys.GLOBAL));
    expected.add(new ExpectedSkill(CourseSkillNames.IMAGE_GENERATION, SkillSceneKeys.GLOBAL));
    expected.add(new ExpectedSkill(CourseSkillNames.COURSE_DRAFT, CourseWorkflowKeys.SCENE));
    expected.add(new ExpectedSkill(CourseSkillNames.WECHAT_CONVERSION, CourseWorkflowKeys.SCENE));
    expected.add(new ExpectedSkill(CourseSkillNames.IMAGE_PLANNING, CourseWorkflowKeys.SCENE));
    expected.add(new ExpectedSkill(CourseSkillNames.FINALIZATION, CourseWorkflowKeys.SCENE));
    expected.add(new ExpectedSkill(CourseSkillNames.AUTHOR_STYLE, CourseWorkflowKeys.SCENE));
    String hot = HotNewsWorkflowKeys.SCENE;
    expected.add(new ExpectedSkill(HotNewsSkillNames.AUTHOR_STYLE, hot));
    expected.add(new ExpectedSkill(HotNewsSkillNames.ANTI_AI, hot));
    expected.add(new ExpectedSkill(HotNewsSkillNames.SKELETON_ILLUSTRATION, hot));
    expected.add(new ExpectedSkill(HotNewsSkillNames.DRAFT_GENERATION, hot));
    expected.add(new ExpectedSkill(HotNewsSkillNames.DRAFT_REVIEW, hot));
    expected.add(new ExpectedSkill(HotNewsSkillNames.WECHAT_CONVERSION, hot));
    expected.add(new ExpectedSkill(HotNewsSkillNames.IMAGE_GENERATION, hot));
    expected.add(new ExpectedSkill(HotNewsSkillNames.THEME_SEARCH_QUERIES, hot));
    expected.add(new ExpectedSkill(HotNewsSkillNames.THEME_GITHUB_QUERY, hot));
    expected.add(new ExpectedSkill(HotNewsSkillNames.THEME_SOURCE_CHANNELS, hot));
    String radar = HotNewsRadarCollectWorkflowKeys.SCENE;
    expected.add(new ExpectedSkill(HotNewsRadarCollectSkillNames.ARTICLE_FACT_BRIEF, radar));
    expected.add(new ExpectedSkill(HotNewsRadarCollectSkillNames.ARTICLE_PUBLISH_DATE, radar));
    expected.add(new ExpectedSkill(HotNewsRadarCollectSkillNames.VIDEO_FACT_BRIEF, radar));
    expected.add(new ExpectedSkill(HotNewsRadarCollectSkillNames.MARKETING_FILTER, radar));
    expected.add(new ExpectedSkill(HotNewsRadarCollectSkillNames.CLUSTER_SUMMARY_MERGE, radar));
    expected.add(new ExpectedSkill(HotNewsRadarCollectSkillNames.AI_THEME_SEED, radar));
    return List.copyOf(expected);
  }

  private static void inspectWorkflow(
      List<BuiltinIssue> issues,
      List<WorkflowRow> graphs,
      Set<String> skillIds,
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
        Object bindingsRaw = node.get(WorkflowGraphMapKeys.SKILL_BINDINGS);
        if (!(bindingsRaw instanceof List<?> bindings)) {
          continue;
        }
        for (Object bindingObj : bindings) {
          if (!(bindingObj instanceof Map<?, ?> binding)) {
            continue;
          }
          Object skillId = binding.get(WorkflowGraphMapKeys.SKILL_ID);
          if (skillId == null || StrUtil.isBlank(String.valueOf(skillId))) {
            issues.add(new BuiltinIssue(BuiltinIssueKind.WORKFLOW, "工作流节点 Skill 绑定为空 key=" + key));
            continue;
          }
          if (!skillIds.contains(String.valueOf(skillId))) {
            issues.add(new BuiltinIssue(
                BuiltinIssueKind.WORKFLOW,
                "工作流绑定了不存在的 Skill key=" + key + " skillId=" + skillId));
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

  private static SkillRow findSkill(List<SkillRow> rows, String name, String scene) {
    for (SkillRow row : rows) {
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
   * 期望的内置 Skill。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class ExpectedSkill {
    /** 名称。 */
    private final String name;
    /** 场景。 */
    private final String scene;
  }
}
