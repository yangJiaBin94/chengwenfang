package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 内容教学模式实体（主键为 code）。 */
@TableName("content_modes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentModeEntity {
  /** 模式编码（主键）。 */
  @TableId("code")
  private String code;
  /** 展示名称。 */
  @TableField("name")
  private String name;
  /** 描述。 */
  @TableField("description")
  private String description;
  /** 状态。 */
  @TableField("status")
  private String status;
  /** 排序。 */
  @TableField("sort_order")
  private Integer sortOrder;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
