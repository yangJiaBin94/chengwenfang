package com.workbench.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 作品在某渠道某格式下的产出。 */
@TableName("work_outputs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOutputEntity {
  /** 主键（原 documentId）。 */
  @TableId("id")
  private String id;
  /** 作品 ID。 */
  @TableField("work_id")
  private String workId;
  /** 渠道 code。 */
  @TableField("channel_code")
  private String channelCode;
  /** 格式 code。 */
  @TableField("format_code")
  private String formatCode;
  /** 标题。 */
  @TableField("title")
  private String title;
  /** 状态。 */
  @TableField("status")
  private String status;
  /** 当前版本号。 */
  @TableField("current_version_no")
  private Integer currentVersionNo;
  /** 当前正文字数。 */
  @TableField("word_count")
  private Integer wordCount;
  /** 当前正文哈希。 */
  @TableField("content_hash")
  private String contentHash;
  /** 乐观锁令牌。 */
  @TableField("version_token")
  private Long versionToken;
  /** 最近工作流运行。 */
  @TableField("workflow_run_id")
  private String workflowRunId;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
