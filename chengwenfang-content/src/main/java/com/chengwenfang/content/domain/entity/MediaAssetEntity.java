package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 作品媒体资产。 */
@TableName("media_assets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAssetEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 作品 ID。 */
  @TableField("work_id")
  private String workId;
  /** 产出 ID。 */
  @TableField("output_id")
  private String outputId;
  /** 来源：generated / synced。 */
  @TableField("origin")
  private String origin;
  /** 媒体类型。 */
  @TableField("media_type")
  private String mediaType;
  /** 用途 kind。 */
  @TableField("usage_kind")
  private String usageKind;
  /** 序号。 */
  @TableField("sequence_no")
  private Integer sequenceNo;
  /** 文件 ID。 */
  @TableField("file_id")
  private String fileId;
  /** 内容哈希。 */
  @TableField("content_hash")
  private String contentHash;
  /** 宽度。 */
  @TableField("width")
  private Integer width;
  /** 高度。 */
  @TableField("height")
  private Integer height;
  /** 替代文本。 */
  @TableField("alt_text")
  private String altText;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
