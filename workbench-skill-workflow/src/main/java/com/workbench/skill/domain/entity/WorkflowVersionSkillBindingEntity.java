package com.workbench.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 某发布版本上节点与 Skill 的冻结绑定快照，对应 workflow_version_skill_bindings。 */
@TableName("workflow_version_skill_bindings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionSkillBindingEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 工作流版本 ID。 */
  @TableField("version_id")
  private String versionId;
  /** 节点 key。 */
  @TableField("node_key")
  private String nodeKey;
  /** Skill ID。 */
  @TableField("skill_id")
  private String skillId;
  /** Skill 版本号。 */
  @TableField("skill_version")
  private Integer skillVersion;
  /** 组合优先级。 */
  @TableField("priority")
  private Integer priority;
  /** 内容哈希。 */
  @TableField("content_hash")
  private String contentHash;
  /** 绑定内容快照，保证历史可复现。 */
  @TableField("snapshot_json")
  private String snapshotJson;
}
