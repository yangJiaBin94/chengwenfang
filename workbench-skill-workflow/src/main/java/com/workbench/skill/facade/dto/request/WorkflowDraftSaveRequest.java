package com.workbench.skill.facade.dto.request;


import com.workbench.skill.facade.dto.view.WorkflowDefinitionDataView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 保存 / 重置 / 校验草稿请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDraftSaveRequest {
  /** 期望的当前修订号（乐观锁）。 */
  private Integer revision;
  /** 定义数据。 */
  private WorkflowDefinitionDataView definition;
  /** 定义 JSON 字符串（兼容旧字段）。 */
  private String definitionJson;
}
