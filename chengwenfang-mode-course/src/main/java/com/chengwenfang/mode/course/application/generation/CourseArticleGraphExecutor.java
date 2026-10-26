package com.chengwenfang.mode.course.application.generation;

import com.chengwenfang.mode.course.application.course.CourseArticleGraphSupport;
import com.chengwenfang.mode.course.application.generation.course.CourseGraphState;
import com.chengwenfang.mode.course.application.generation.course.CourseGraphStateKeys;
import com.chengwenfang.skill.facade.constant.WorkflowNodeHandlerOrders;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import com.chengwenfang.skill.facade.spi.WorkflowNodeHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 课程成文工作流节点调度器：按 {@link CourseWorkflowNodeType} 分发到 {@link CourseArticleGraphSupport}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseArticleGraphExecutor implements WorkflowNodeHandler {
  /** 课程图业务支持。 */
  private final CourseArticleGraphSupport graphSupport;

  /**
   * @param typeKey 节点类型键
   * @return 是否为课程规范节点
   */
  @Override
  public boolean supports(String typeKey) {
    return CourseWorkflowNodeType.isCourseTypeKey(typeKey);
  }

  /**
   * @return 课程节点路由优先级
   */
  @Override
  public int order() {
    return WorkflowNodeHandlerOrders.COURSE;
  }

  /**
   * 执行单个课程节点，返回写回 GraphRuntime 的状态增量。
   *
   * @param typeKey       节点类型键
   * @param nodeKey       节点实例键
   * @param nodeName      展示名
   * @param skillBindings 原始 Skill 绑定 Map 列表
   * @param state         图运行状态
   * @param config        节点配置
   * @return 状态增量
   */
  @Override
  public Map<String, Object> execute(
      String typeKey,
      String nodeKey,
      String nodeName,
      List<Map<String, Object>> skillBindings,
      Map<String, Object> state,
      Map<String, Object> config) {
    CourseWorkflowNodeType nodeType = CourseWorkflowNodeType.from(typeKey)
        .orElseThrow(() -> new IllegalArgumentException("未知课程节点类型：" + typeKey));
    log.info("课程图节点分发 typeKey={} nodeKey={}", typeKey, nodeKey);
    CourseGraphState graphState = CourseGraphState.fromMap(state);
    List<SkillPromptBindingView> bindings = toPromptBindings(skillBindings);
    try {
      dispatch(nodeType, graphState, bindings);
    } catch (Exception exception) {
      if (exception instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException(
          exception.getMessage() == null ? "课程节点执行失败" : exception.getMessage(),
          exception);
    }
    Map<String, Object> output = graphState.toMap();
    output.put(CourseGraphStateKeys.COURSE_STEP, nodeType.getValue());
    if (CourseWorkflowNodeType.isDraftSection(typeKey) && graphState.getDraftParts() != null) {
      output.put(typeKey, graphState.getDraftParts().isEmpty()
          ? ""
          : graphState.getDraftParts().get(graphState.getDraftParts().size() - 1));
    }
    if (nodeType == CourseWorkflowNodeType.REVIEW_DRAFT && graphState.getDraft() != null) {
      output.put(nodeType.getValue(), graphState.getDraft());
    }
    if (nodeType == CourseWorkflowNodeType.WECHAT_CONVERSION && graphState.getWechat() != null) {
      output.put(nodeType.getValue(), graphState.getWechat());
    }
    return output;
  }

  /**
   * 按节点类型分发业务方法。
   *
   * @param nodeType  节点枚举
   * @param state     图状态
   * @param bindings  Skill 绑定
   */
  private void dispatch(
      CourseWorkflowNodeType nodeType,
      CourseGraphState state,
      List<SkillPromptBindingView> bindings) throws Exception {
    switch (nodeType) {
      case SNAPSHOT_CONTEXT -> graphSupport.snapshotContext(state, bindings);
      case DRAFT_OPENING, DRAFT_CORE, DRAFT_EXAMPLE, DRAFT_SUMMARY, DRAFT_PREVIEW ->
          graphSupport.generateDraftSection(state, nodeType.getValue(), bindings);
      case REVIEW_DRAFT -> graphSupport.reviewDraft(state);
      case WECHAT_CONVERSION -> graphSupport.convertWechat(state, bindings);
      case IMAGE_COVER -> graphSupport.generateCover(state);
      case IMAGE_INLINE -> graphSupport.generateInline(state);
      case FORMULA_STYLE_VALIDATION -> graphSupport.validateFormulaStyle(state);
      case FORMULA_EXPRESSION_VALIDATION -> graphSupport.validateFormulaExpression(state);
      case FINAL_VALIDATION -> graphSupport.validateFinal(state);
      case COMMIT_VERSION -> graphSupport.commitVersion(state);
      default -> log.warn("课程节点无业务分支 typeKey={}", nodeType.getValue());
    }
  }

  /**
   * 将快照中的绑定 Map 转为类型化绑定视图。
   *
   * @param skillBindings 原始绑定列表
   * @return 绑定视图列表
   */
  private static List<SkillPromptBindingView> toPromptBindings(List<Map<String, Object>> skillBindings) {
    if (skillBindings == null || skillBindings.isEmpty()) {
      return List.of();
    }
    List<SkillPromptBindingView> result = new ArrayList<>();
    for (Map<String, Object> binding : skillBindings) {
      if (binding == null) {
        continue;
      }
      Object priority = binding.get("priority");
      result.add(SkillPromptBindingView.builder()
          .skillId(binding.get("skillId") == null ? null : String.valueOf(binding.get("skillId")))
          .priority(priority instanceof Number number ? number.intValue() : 100)
          .enabled(binding.get("enabled") == null || Boolean.TRUE.equals(binding.get("enabled")))
          .build());
    }
    return result;
  }
}
