package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 作品：各模式生产单元。 */
@TableName("works")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkEntity {
  /** 主键（原 courseId）。 */
  @TableId("id")
  private String id;
  /** 所属模式 code。 */
  @TableField("mode_code")
  private String modeCode;
  /** 所属主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 作品标题。 */
  @TableField("title")
  private String title;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
