package com.chengwenfang.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题深度调研任务运行记录。 */
@TableName("hotnews_research_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicResearchRunEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 选题/话题 ID。 */
  @TableField("topic_id")
  private String topicId;
  /** 异步任务 ID。 */
  @TableField("job_id")
  private String jobId;
  /** 研究运行状态。 */
  @TableField("status")
  private String status;
  /** 错误信息。 */
  @TableField("error_message")
  private String errorMessage;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 结束时间。 */
  @TableField("finished_at")
  private LocalDateTime finishedAt;
}
