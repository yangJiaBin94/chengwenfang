package com.chengwenfang.skill.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.skill.domain.entity.WorkflowRunEntity;
import com.chengwenfang.skill.domain.entity.WorkflowRunExecutedNodeEntity;
import com.chengwenfang.skill.infrastructure.mapper.WorkflowRunExecutedNodeMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 工作流运行态指标与 stateSummary 查询。
 */
@Component
@RequiredArgsConstructor
public class WorkflowMetricsQuery {

  /** 运行已执行节点序列。 */
  private final WorkflowRunExecutedNodeMapper executedNodeMapper;

  /**
   * 从 error_message 与已执行节点子表组装原 stateSummary 形状。
   *
   * @param run 运行实体
   * @return LinkedHashMap（executed → error）
   */
  public Map<String, Object> assembleStateSummary(WorkflowRunEntity run) {
    List<String> executed = executedNodeMapper.selectList(new LambdaQueryWrapper<WorkflowRunExecutedNodeEntity>()
            .eq(WorkflowRunExecutedNodeEntity::getWorkflowRunId, run.getId())
            .orderByAsc(WorkflowRunExecutedNodeEntity::getSeq))
        .stream()
        .map(WorkflowRunExecutedNodeEntity::getNodeKey)
        .toList();
    return WorkflowRunStateSummaryAssembler.assemble(executed, run.getErrorMessage());
  }


}
