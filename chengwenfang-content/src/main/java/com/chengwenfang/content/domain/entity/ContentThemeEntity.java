package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 内容主题实体。 */
@TableName("themes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentThemeEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 所属模式 code。 */
  @TableField("mode_code")
  private String modeCode;
  /** 主题编码。 */
  @TableField("code")
  private String code;
  /** 主题名称。 */
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
  /** 创建途径。 */
  @TableField("created_via")
  private String createdVia;
  /** 软删除时间；空表示未删除。 */
  @TableField("deleted_at")
  private LocalDateTime deletedAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
