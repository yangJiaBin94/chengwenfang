package com.chengwenfang.publishing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发布渲染快照：HTML 存文件（原 publishing_snapshots → render_snapshots）。 */
@TableName("render_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishingSnapshotEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 产出 ID（原 document_id）。 */
  @TableField("output_id")
  private String outputId;
  /** 快照对应的正文版本号。 */
  @TableField("output_version_no")
  private Integer outputVersionNo;
  /** 使用的主题版本。 */
  @TableField("layout_theme_version_id")
  private String layoutThemeVersionId;
  /** 排版哈希。 */
  @TableField("layout_hash")
  private String layoutHash;
  /** Markdown 哈希。 */
  @TableField("markdown_hash")
  private String markdownHash;
  /** 渲染 HTML 文件 ID。 */
  @TableField("html_file_id")
  private String htmlFileId;
  /** HTML 哈希。 */
  @TableField("html_hash")
  private String htmlHash;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
