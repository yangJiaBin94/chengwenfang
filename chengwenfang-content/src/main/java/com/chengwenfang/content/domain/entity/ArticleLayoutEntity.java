package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 产出排版选择：主题版本（原 article_layouts → output_layouts）。 */
@TableName("output_layouts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleLayoutEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 产出 ID（原 document_id）。 */
  @TableField("output_id")
  private String outputId;
  /** 排版所对应的正文版本号（原 article_version_no）。 */
  @TableField("output_version_no")
  private Integer outputVersionNo;
  /** 排版主题版本 ID（原 theme_version_id）。 */
  @TableField("layout_theme_version_id")
  private String layoutThemeVersionId;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
