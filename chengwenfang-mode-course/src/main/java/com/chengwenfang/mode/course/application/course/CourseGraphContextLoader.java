package com.chengwenfang.mode.course.application.course;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.mode.course.application.generation.course.CourseGraphState;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.skill.facade.WorkflowFacade;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.dto.view.WorkflowDefinitionDataView;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 课程成文图：工作流快照与生成上下文加载、进度同步与文本产物落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseGraphContextLoader {
  /** 工作流门面。 */
  private final WorkflowFacade workflows;
  /** 任务门面。 */
  private final JobFacade jobs;
  /** 课程生成上下文构建器。 */
  private final CourseGenerationContextBuilder contextBuilder;
  /** Jackson。 */
  private final ObjectMapper objectMapper;

  /**
   * 加载已发布工作流快照与课程上下文；续跑配图时回填原稿/公众号。
   *
   * @param state         图状态
   * @param skillBindings 未使用（节点签名对齐）
   */
  public void snapshotContext(CourseGraphState state, List<SkillPromptBindingView> skillBindings) {
    String courseId = state.getCourseId();
    String workflowId = state.getWorkflowId();
    log.info("课程图节点 snapshot_context courseId={} workflowId={}", courseId, workflowId);
    if (StrUtil.isBlank(workflowId)) {
      throw new IllegalStateException("WORKFLOW_REQUIRED: 正式生成需要 workflowId");
    }
    WorkflowDefinitionDataView snapshotData = workflows.loadPublishedSnapshot(workflowId);
    Map<String, Object> snapshot = objectMapper.convertValue(snapshotData, new TypeReference<>() {});
    state.setWorkflowSnapshot(snapshot);
    CourseGenerationContext ctx = resolveContext(state);
    ctx.requirePlanBoundary();
    state.setContextSummary(ctx.toSummaryMap());
    state.setTitle(ctx.title());
    state.setDraftArticleId(ctx.draftArticleId());
    state.setWechatArticleId(ctx.wechatArticleId());
    state.setWordCountOption(ctx.wordCountOption());
    state.setImageCountOption(ctx.imageCountOption());
    if (Boolean.TRUE.equals(state.getResumeImagesOnly())) {
      String draft = loadResumeDraft(state.getJobId());
      String wechat = loadResumeWechat(state.getJobId());
      if (StrUtil.isBlank(draft) || StrUtil.isBlank(wechat)) {
        throw new IllegalStateException(
            "WORKFLOW_ARTIFACTS_REQUIRED: 重试图片步骤缺少本次运行的原稿或公众号中间产物");
      }
      wechat = CourseMarkdownUtil.normalizeTerminalSections(wechat);
      wechat = CourseMarkdownUtil.normalizeStandaloneCodeLines(wechat);
      state.setDraft(draft);
      state.setWechat(wechat);
    }
    markProgress(state, CourseWorkflowNodeType.SNAPSHOT_CONTEXT);
  }

  /**
   * 解析课程生成上下文。
   *
   * @param state 图状态
   * @return 上下文
   */
  public CourseGenerationContext resolveContext(CourseGraphState state) {
    return contextBuilder.build(state.getCourseId(), state.toContextBuilderInput());
  }

  /**
   * 读取续跑用原稿产物。
   *
   * @param jobId 任务 ID
   * @return 原稿文本；可能为空
   */
  String loadResumeDraft(String jobId) {
    String draft = workflows.latestSucceededArtifactText(
        jobId, CourseWorkflowNodeType.REVIEW_DRAFT.getValue(), "course_draft");
    if (StrUtil.isNotBlank(draft)) {
      return draft;
    }
    return workflows.latestSucceededArtifactText(jobId, "generate_draft", "assembled_draft");
  }

  /**
   * 读取续跑用公众号产物。
   *
   * @param jobId 任务 ID
   * @return 公众号文本；可能为空
   */
  String loadResumeWechat(String jobId) {
    return workflows.latestSucceededArtifactText(
        jobId, CourseWorkflowNodeType.WECHAT_CONVERSION.getProgressStepKey(), "assembled_wechat");
  }

  /**
   * 按节点类型同步进度。
   *
   * @param state    图状态
   * @param nodeType 节点类型
   */
  public void markProgress(CourseGraphState state, CourseWorkflowNodeType nodeType) {
    if (state == null || nodeType == null) {
      return;
    }
    reportJobStep(
        state.getJobId(),
        nodeType.getProgressStepKey(),
        progressPercent(nodeType),
        nodeType.getDisplayName());
  }

  /**
   * 同步 workflow_runs.current_node 与 jobs 进度事件，供前端执行页推进。
   *
   * @param jobId    任务 ID
   * @param stepKey  进度步名
   * @param progress 0–100
   * @param message  人类可读说明
   */
  public void reportJobStep(String jobId, String stepKey, int progress, String message) {
    if (StrUtil.isBlank(jobId) || StrUtil.isBlank(stepKey)) {
      return;
    }
    workflows.markJobRunProgress(jobId, stepKey);
    jobs.updateProgress(jobId, stepKey, progress, StrUtil.blankToDefault(message, stepKey));
  }

  /**
   * 规范节点对应的大致进度百分比。
   *
   * @param nodeType 节点类型
   * @return 0–100
   */
  private static int progressPercent(CourseWorkflowNodeType nodeType) {
    return switch (nodeType) {
      case SNAPSHOT_CONTEXT -> 5;
      case DRAFT_OPENING -> 15;
      case DRAFT_CORE -> 25;
      case DRAFT_EXAMPLE -> 35;
      case DRAFT_SUMMARY -> 42;
      case DRAFT_PREVIEW -> 48;
      case REVIEW_DRAFT -> 52;
      case WECHAT_CONVERSION -> 60;
      case IMAGE_COVER -> 75;
      case IMAGE_INLINE -> 85;
      case FORMULA_STYLE_VALIDATION -> 90;
      case FORMULA_EXPRESSION_VALIDATION -> 92;
      case FINAL_VALIDATION -> 94;
      case COMMIT_VERSION -> 96;
    };
  }

  /**
   * 写入带类型的文本产物（另开 node_run，nodeKey 供复用/续跑查询）。
   *
   * @param jobId         任务 ID
   * @param nodeKey       产物所属节点键
   * @param inputSummary  输入摘要（参与复用哈希）
   * @param artifactType  产物类型
   * @param contentText   文本正文
   * @param outputSummary 输出摘要
   */
  public void persistTextArtifact(
      String jobId,
      String nodeKey,
      Map<String, Object> inputSummary,
      String artifactType,
      String contentText,
      Map<String, Object> outputSummary) {
    if (StrUtil.isBlank(jobId) || StrUtil.isBlank(nodeKey) || StrUtil.isBlank(artifactType)) {
      return;
    }
    if (StrUtil.isBlank(contentText)) {
      return;
    }
    String nodeRunId = workflows.beginNodeRun(
        jobId, nodeKey, 0, inputSummary == null ? Map.of() : inputSummary);
    if (StrUtil.isBlank(nodeRunId)) {
      return;
    }
    workflows.completeNodeRun(
        nodeRunId,
        true,
        outputSummary == null ? Map.of() : outputSummary,
        artifactType,
        contentText,
        null,
        null);
  }

  /**
   * 按 typeKey 查找快照节点。
   *
   * @param snapshot 工作流快照
   * @param typeKey  节点 typeKey
   * @return 节点 Map；未找到为 null
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> findNodeByTypeKey(Map<String, Object> snapshot, String typeKey) {
    Object nodesRaw = snapshot == null ? null : snapshot.get("nodes");
    if (!(nodesRaw instanceof List<?> nodes)) {
      return null;
    }
    for (Object raw : nodes) {
      if (!(raw instanceof Map<?, ?> map)) {
        continue;
      }
      Map<String, Object> node = (Map<String, Object>) map;
      String resolved = String.valueOf(node.getOrDefault("typeKey", node.getOrDefault("type", "")));
      if (typeKey.equals(resolved) || typeKey.equals(String.valueOf(node.get("key")))) {
        return node;
      }
    }
    return null;
  }

  /**
   * 从节点 Map 解析 skillBindings。
   *
   * @param node 节点
   * @return 绑定列表；无则空列表
   */
  @SuppressWarnings("unchecked")
  public static List<SkillPromptBindingView> skillBindingsOf(Map<String, Object> node) {
    if (node == null || !(node.get("skillBindings") instanceof List<?> list)) {
      return List.of();
    }
    List<SkillPromptBindingView> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> binding = (Map<String, Object>) map;
        Object priority = binding.get("priority");
        out.add(SkillPromptBindingView.builder()
            .skillId(binding.get("skillId") == null ? null : String.valueOf(binding.get("skillId")))
            .priority(priority instanceof Number number ? number.intValue() : 100)
            .enabled(binding.get("enabled") == null || Boolean.TRUE.equals(binding.get("enabled")))
            .build());
      }
    }
    return out;
  }

  /**
   * 将绑定视图转为节点 Map 结构。
   *
   * @param bindings 绑定列表
   * @return Map 列表
   */
  public static List<Map<String, Object>> bindingsToMaps(List<SkillPromptBindingView> bindings) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (SkillPromptBindingView binding : bindings) {
      if (binding == null) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("skillId", binding.getSkillId());
      row.put("priority", binding.getPriority() == null ? 100 : binding.getPriority());
      row.put("enabled", binding.getEnabled() == null || binding.getEnabled());
      out.add(row);
    }
    return out;
  }

  /**
   * 截取文本尾部。
   *
   * @param text 源文本
   * @param max  最大长度
   * @return 尾部；null 视为空串
   */
  public static String tail(String text, int max) {
    if (text == null) {
      return "";
    }
    String stripped = text.strip();
    if (stripped.length() <= max) {
      return stripped;
    }
    return stripped.substring(stripped.length() - max);
  }

  /**
   * 对象转字符串。
   *
   * @param value 任意值
   * @return 字符串；null 为空串
   */
  public static String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * @return 工作流门面（供同包协作类复用产物查询）
   */
  WorkflowFacade workflows() {
    return workflows;
  }
}
