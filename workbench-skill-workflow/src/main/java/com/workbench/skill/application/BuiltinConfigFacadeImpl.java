package com.workbench.skill.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.skill.domain.entity.SkillEntity;
import com.workbench.skill.domain.entity.WorkflowDefinitionEntity;
import com.workbench.skill.domain.entity.WorkflowVersionEntity;
import com.workbench.skill.facade.BuiltinConfigFacade;
import com.workbench.skill.facade.builtin.BuiltinConfigInspector;
import com.workbench.skill.facade.builtin.BuiltinConfigInspector.SkillRow;
import com.workbench.skill.facade.builtin.BuiltinConfigInspector.WorkflowRow;
import com.workbench.skill.facade.builtin.BuiltinIssue;
import com.workbench.skill.facade.dto.view.BuiltinInspectView;
import com.workbench.skill.infrastructure.mapper.SkillMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowDefinitionMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowVersionMapper;
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

  /** Skill 表。 */
  private final SkillMapper skillMapper;
  /** 工作流定义表。 */
  private final WorkflowDefinitionMapper definitionMapper;
  /** 工作流版本表。 */
  private final WorkflowVersionMapper versionMapper;

  /**
   * {@inheritDoc}
   */
  @Override
  public BuiltinInspectView inspect() {
    List<SkillRow> skillRows = loadSkills();
    List<WorkflowRow> workflowRows = loadWorkflows();
    List<BuiltinIssue> issues = BuiltinConfigInspector.inspect(skillRows, workflowRows);
    return BuiltinInspectView.builder()
        .ready(issues.isEmpty())
        .issues(BuiltinConfigInspector.messageList(issues))
        .bootstrapHint(BuiltinConfigInspector.BOOTSTRAP_HINT)
        .skillCount(skillRows.size())
        .expectedSkillCount(BuiltinConfigInspector.expectedSkills().size())
        .workflowReady(!BuiltinConfigInspector.hasWorkflowIssue(issues))
        .build();
  }

  /**
   * 加载未删除 Skill 为体检行。
   *
   * @return Skill 行
   */
  private List<SkillRow> loadSkills() {
    List<SkillEntity> entities = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
        .isNull(SkillEntity::getDeletedAt));
    List<SkillRow> rows = new ArrayList<>();
    for (SkillEntity entity : entities) {
      rows.add(new SkillRow(
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
