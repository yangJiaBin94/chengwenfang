package com.chengwenfang.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 产出正文版本（正文在 stored_files）。 */
@TableName("work_output_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOutputVersionEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 产出 ID。 */
  @TableField("output_id")
  private String outputId;
  /** 版本号。 */
  @TableField("version_no")
  private Integer versionNo;
  /** 正文文件 ID。 */
  @TableField("body_file_id")
  private String bodyFileId;
  /** 正文哈希。 */
  @TableField("content_hash")
  private String contentHash;
  /** 版本来源。 */
  @TableField("source")
  private String source;
  /** 产生原因。 */
  @TableField("reason")
  private String reason;
  /** 关联工作流运行。 */
  @TableField("workflow_run_id")
  private String workflowRunId;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
