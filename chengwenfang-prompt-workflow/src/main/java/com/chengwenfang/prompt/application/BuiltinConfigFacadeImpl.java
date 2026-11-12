package com.chengwenfang.prompt.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.prompt.domain.entity.PromptPackEntity;
import com.chengwenfang.prompt.domain.entity.WorkflowDefinitionEntity;
import com.chengwenfang.prompt.domain.entity.WorkflowVersionEntity;
import com.chengwenfang.prompt.facade.BuiltinConfigFacade;
import com.chengwenfang.prompt.facade.builtin.BuiltinConfigInspector;
import com.chengwenfang.prompt.facade.builtin.BuiltinConfigInspector.PromptPackRow;
import com.chengwenfang.prompt.facade.builtin.BuiltinConfigInspector.WorkflowRow;
import com.chengwenfang.prompt.facade.builtin.BuiltinIssue;
import com.chengwenfang.prompt.facade.dto.view.BuiltinInspectView;
import com.chengwenfang.prompt.infrastructure.mapper.PromptPackMapper;
import com.chengwenfang.prompt.infrastructure.mapper.WorkflowDefinitionMapper;
import com.chengwenfang.prompt.infrastructure.mapper.WorkflowVersionMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 内置配置体检门面实现：读库后委托 {@link BuiltinConfigInspector}。
 */
@Service
@RequiredArgsConstructor
public class BuiltinConfigFacadeImpl implements BuiltinConfigFacade {

  /** 提示词包 表。 */
  private final PromptPackMapper promptPackMapper;
  /** 工作流定义表。 */
  private final WorkflowDefinitionMapper definitionMapper;
  /** 工作流版本表。 */
  private final WorkflowVersionMapper versionMapper;

  /**
   * {@inheritDoc}
   */
  @Override
  public BuiltinInspectView inspect() {
    List<PromptPackRow> promptPackRows = loadPromptPacks();
    List<WorkflowRow> workflowRows = loadWorkflows();
    List<BuiltinIssue> issues = BuiltinConfigInspector.inspect(promptPackRows, workflowRows);
    return BuiltinInspectView.builder()
        .ready(issues.isEmpty())
        .issues(BuiltinConfigInspector.messageList(issues))
        .bootstrapHint(BuiltinConfigInspector.BOOTSTRAP_HINT)
        .promptPackCount(promptPackRows.size())
        .expectedPromptPackCount(BuiltinConfigInspector.expectedPromptPacks().size())
        .workflowReady(!BuiltinConfigInspector.hasWorkflowIssue(issues))
        .build();
  }

  /**
   * 加载未删除 提示词包 为体检行。
   *
   * @return 提示词包 行
   */
  private List<PromptPackRow> loadPromptPacks() {
    List<PromptPackEntity> entities = promptPackMapper.selectList(new LambdaQueryWrapper<PromptPackEntity>()
        .isNull(PromptPackEntity::getDeletedAt));
    List<PromptPackRow> rows = new ArrayList<>();
    for (PromptPackEntity entity : entities) {
      rows.add(new PromptPackRow(
          entity.getId(),
          entity.getName(),
          entity.getScene(),
          Boolean.TRUE.equals(entity.getEnabled()),
          entity.getSystemInstructions()));
    }
    return rows;
  }

  /**
   * 加载工作流定义及已发布快照 JSON。
   *
   * @return 工作流行
   */
  private List<WorkflowRow> loadWorkflows() {
    List<WorkflowDefinitionEntity> definitions = definitionMapper.selectList(null);
    List<WorkflowRow> rows = new ArrayList<>();
    for (WorkflowDefinitionEntity definition : definitions) {
      String snapshot = null;
      if (StrUtil.isNotBlank(definition.getPublishedVersionId())) {
        WorkflowVersionEntity version = versionMapper.selectById(definition.getPublishedVersionId());
        if (version != null) {
          snapshot = version.getSnapshotJson();
        }
      }
      rows.add(new WorkflowRow(definition.getWorkflowKey(), definition.getPublishedVersionId(), snapshot));
    }
    return rows;
  }
}
