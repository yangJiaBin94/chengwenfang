package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主题画像列表项实体。 */
@TableName("theme_profile_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeProfileItemEntity {
  /** 主键。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 画像种类。 */
  @TableField("profile_kind")
  private String profileKind;
  /** 列表类型。 */
  @TableField("item_type")
  private String itemType;
  /** 列表内顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 列表项内容。 */
  @TableField("content")
  private String content;
}
