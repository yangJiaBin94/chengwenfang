package com.chengwenfang.prompt.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.prompt.domain.entity.PromptPackEntity;
import com.chengwenfang.prompt.domain.entity.WorkflowDefinitionEntity;
import com.chengwenfang.prompt.domain.entity.WorkflowVersionEntity;
import com.chengwenfang.prompt.domain.entity.WorkflowVersionPromptPackBindingEntity;
import com.chengwenfang.prompt.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.prompt.facade.constant.HotNewsRadarCollectWorkflowKeys;
import com.chengwenfang.prompt.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.prompt.facade.enums.WorkflowFeatureBinding;
import com.chengwenfang.prompt.facade.dto.view.WorkflowDefinitionDataView;
import com.chengwenfang.prompt.facade.dto.view.WorkflowFeatureAvailabilityView;
import com.chengwenfang.prompt.facade.dto.view.WorkflowSummaryView;
import com.chengwenfang.prompt.infrastructure.mapper.PromptPackMapper;
import com.chengwenfang.prompt.infrastructure.mapper.WorkflowDefinitionMapper;
import com.chengwenfang.prompt.infrastructure.mapper.WorkflowVersionMapper;
import com.chengwenfang.prompt.infrastructure.mapper.WorkflowVersionPromptPackBindingMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 工作流解析：按场景/功能绑定反查已发布定义、加载快照与 提示词包 摘要。
 * <p>由 WorkflowFacadeImpl 薄委托，不对外暴露。
 */
@Service
@RequiredArgsConstructor
public class WorkflowResolveService {

  /** 工作流定义表。 */
  private final WorkflowDefinitionMapper definitionMapper;
  /** 已发布版本表。 */
  private final WorkflowVersionMapper versionMapper;
  /** 版本 提示词包绑定表。 */
  private final WorkflowVersionPromptPackBindingMapper bindingMapper;
  /** 提示词包 表。 */
  private final PromptPackMapper promptPackMapper;
  /** 共享工具。 */
  private final WorkflowSupport support;

  /**
   * 汇总已发布版本上的 提示词包绑定，供入队 promptPackSnapshot。
   */
  public List<Map<String, Object>> promptPackSnapshotForWorkflow(String workflowId) {
    if (workflowId == null || workflowId.isBlank()) {
      return List.of();
    }
    WorkflowDefinitionEntity def = definitionMapper.selectById(workflowId);
    if (def == null || def.getPublishedVersionId() == null || def.getPublishedVersionId().isBlank()) {
      return List.of();
    }
    List<WorkflowVersionPromptPackBindingEntity> bindings = bindingMapper.selectList(
        new LambdaQueryWrapper<WorkflowVersionPromptPackBindingEntity>()
            .eq(WorkflowVersionPromptPackBindingEntity::getVersionId, def.getPublishedVersionId())
            .orderByAsc(WorkflowVersionPromptPackBindingEntity::getPriority));
    if (bindings.isEmpty()) {
      return List.of();
    }
    Map<String, Map<String, Object>> byPromptPack = new LinkedHashMap<>();
    for (WorkflowVersionPromptPackBindingEntity binding : bindings) {
      if (binding.getPromptPackId() == null || binding.getPromptPackId().isBlank()) {
        continue;
      }
      Map<String, Object> row = byPromptPack.computeIfAbsent(binding.getPromptPackId(), id -> {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", id);
        PromptPackEntity promptPack = promptPackMapper.selectById(id);
        summary.put("name", promptPack == null || promptPack.getName() == null ? id : promptPack.getName());
        summary.put("scope", promptPack == null || promptPack.getScope() == null ? "scene" : promptPack.getScope());
        summary.put("priority", binding.getPriority() == null ? 100 : binding.getPriority());
        summary.put("workflowType", def.getScene() == null ? "" : def.getScene());
        summary.put("nodeKeys", new ArrayList<String>());
        return summary;
      });
      @SuppressWarnings("unchecked")
      List<String> nodeKeys = (List<String>) row.get("nodeKeys");
      if (binding.getNodeKey() != null && !binding.getNodeKey().isBlank()
          && !nodeKeys.contains(binding.getNodeKey())) {
        nodeKeys.add(binding.getNodeKey());
      }
    }
    return new ArrayList<>(byPromptPack.values());
  }
  /**
   * 按场景解析已发布且启用的工作流；课程生成场景优先匹配 key {@code course-content-generation}。
   *
   * @param scene 场景码；空白默认 {@code course_content_generation}
   * @return 匹配到的已发布定义摘要
   * @throws BusinessException 该场景无已发布工作流，或已停用
   */
  public WorkflowSummaryView resolvePublishedByScene(String scene) {
    String normalized = scene == null || scene.isBlank() ? CourseWorkflowKeys.SCENE : scene;
    WorkflowFeatureBinding binding = WorkflowFeatureBinding.fromScene(normalized);
    // 课程场景优先固定 key，避免多条同 scene 时选错
    if (CourseWorkflowKeys.SCENE.equals(normalized)) {
      WorkflowDefinitionEntity preferred = definitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
          .eq(WorkflowDefinitionEntity::getWorkflowKey, CourseWorkflowKeys.DEFINITION_KEY)
          .isNotNull(WorkflowDefinitionEntity::getPublishedVersionId)
          .last("LIMIT 1"));
      if (preferred != null) {
        support.assertDefinitionRunnable(preferred, binding);
        return support.toSummary(preferred);
      }
    }
    if (HotNewsWorkflowKeys.SCENE.equals(normalized)) {
      WorkflowDefinitionEntity preferred = definitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
          .eq(WorkflowDefinitionEntity::getWorkflowKey, HotNewsWorkflowKeys.DEFINITION_KEY)
          .isNotNull(WorkflowDefinitionEntity::getPublishedVersionId)
          .last("LIMIT 1"));
      if (preferred != null) {
        support.assertDefinitionRunnable(preferred, binding);
        return support.toSummary(preferred);
      }
    }
    if (HotNewsRadarCollectWorkflowKeys.SCENE.equals(normalized)) {
      WorkflowDefinitionEntity preferred = definitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
          .eq(WorkflowDefinitionEntity::getWorkflowKey, HotNewsRadarCollectWorkflowKeys.DEFINITION_KEY)
          .isNotNull(WorkflowDefinitionEntity::getPublishedVersionId)
          .last("LIMIT 1"));
      if (preferred != null) {
        support.assertDefinitionRunnable(preferred, binding);
        return support.toSummary(preferred);
      }
    }
    // 其它场景：取同 scene 下最近更新的已发布定义
    WorkflowDefinitionEntity match = definitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
        .eq(WorkflowDefinitionEntity::getScene, normalized)
        .isNotNull(WorkflowDefinitionEntity::getPublishedVersionId)
        .orderByDesc(WorkflowDefinitionEntity::getUpdatedAt)
        .last("LIMIT 1"));
    if (match == null) {
      String message = binding == null
          ? "场景无已发布工作流：scene=" + normalized
          : binding.unavailableMessage();
      throw new BusinessException(ErrorCodes.WORKFLOW_UNAVAILABLE, message, 409);
    }
    support.assertDefinitionRunnable(match, binding);
    return support.toSummary(match);
  }

  /**
   * 按功能绑定反查已发布且启用的工作流。
   *
   * @param binding 功能绑定；null 时抛校验错误
   * @return 可用工作流摘要
   */
  public WorkflowSummaryView requireActivePublished(WorkflowFeatureBinding binding) {
    if (binding == null) {
      throw new BusinessException(ErrorCodes.WORKFLOW_INVALID, "功能工作流绑定不能为空", 400);
    }
    return resolvePublishedByScene(binding.getScene());
  }

  /**
   * 按功能键反查工作流可用性（不抛错）。
   *
   * @param featureKey 功能键
   * @return 可用性视图
   */
  public WorkflowFeatureAvailabilityView getFeatureAvailability(String featureKey) {
    WorkflowFeatureBinding binding = WorkflowFeatureBinding.fromFeatureKey(featureKey);
    if (binding == null) {
      return WorkflowFeatureAvailabilityView.builder()
          .featureKey(StrUtil.nullToEmpty(featureKey))
          .available(false)
          .message("未知功能，无法反查工作流：" + StrUtil.nullToEmpty(featureKey))
          .build();
    }
    try {
      WorkflowSummaryView published = requireActivePublished(binding);
      return WorkflowFeatureAvailabilityView.builder()
          .featureKey(binding.getFeatureKey())
          .available(true)
          .workflowId(published.getId())
          .name(published.getName())
          .status(published.getStatus())
          .publishedVersionId(published.getPublishedVersionId())
          .build();
    } catch (BusinessException exception) {
      return WorkflowFeatureAvailabilityView.builder()
          .featureKey(binding.getFeatureKey())
          .available(false)
          .name(binding.getDisplayName())
          .message(StrUtil.blankToDefault(exception.getMessage(), binding.unavailableMessage()))
          .build();
    }
  }

  /**
   * 加载工作流已发布版本的图快照为结构化定义。
   *
   * @param workflowId 工作流定义主键
   * @return 已发布快照的定义数据
   * @throws BusinessException 未发布或快照缺失
   */
  public WorkflowDefinitionDataView loadPublishedSnapshot(String workflowId) {
    WorkflowDefinitionEntity def = support.require(workflowId);
    // 定义上必须已挂 publishedVersionId
    if (def.getPublishedVersionId() == null || def.getPublishedVersionId().isBlank()) {
      throw new BusinessException(
          "workflow_not_published",
          "工作流尚无已发布版本：" + workflowId,
          409);
    }
    WorkflowVersionEntity published = versionMapper.selectById(def.getPublishedVersionId());
    // 版本行或快照 JSON 缺失都视为不可用
    if (published == null || published.getSnapshotJson() == null || published.getSnapshotJson().isBlank()) {
      throw new BusinessException(
          "workflow_snapshot_missing",
          "已发布快照缺失，工作流：" + workflowId,
          409);
    }
    return support.toDefinitionData(support.readDefinition(published.getSnapshotJson(), def));
  }

}
