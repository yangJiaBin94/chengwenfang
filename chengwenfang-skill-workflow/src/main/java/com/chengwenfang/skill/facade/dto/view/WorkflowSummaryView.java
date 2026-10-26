package com.chengwenfang.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流列表/摘要视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSummaryView {
  /** 工作流 ID。 */
  private String id;
  /** 业务 key。 */
  private String key;
  /** 名称。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 场景。 */
  private String scene;
  /** 状态。 */
  private String status;
  /** 负责人。 */
  private String owner;
  /** 已发布版本 ID。 */
  private String publishedVersionId;
  /** 已发布版本号。 */
  private String publishedVersion;
  /** 草稿修订号。 */
  private Integer draftRevision;
  /** 更新时间（ISO-8601）。 */
  private String updatedAt;
  /** 归档等操作成功标记（可选）。 */
  private Boolean ok;
}
