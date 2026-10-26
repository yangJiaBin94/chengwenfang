package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修复运行消除或新增的问题码，对应 {@code repair_run_codes}。
 */
@TableName("repair_run_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairRunCodeEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 修复运行 ID。 */
  @TableField("repair_run_id")
  private String repairRunId;
  /** 类型：resolved / added。 */
  @TableField("code_type")
  private String codeType;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 规则码。 */
  @TableField("code")
  private String code;
}
