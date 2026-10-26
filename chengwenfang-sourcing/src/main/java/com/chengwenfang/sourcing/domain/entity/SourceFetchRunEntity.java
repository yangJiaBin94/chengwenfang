package com.chengwenfang.sourcing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一次信源抓取任务运行记录。 */
@TableName("source_fetch_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceFetchRunEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 采集运行状态。 */
  @TableField("status")
  private String status;
  /** 回溯抓取窗口（小时）。 */
  @TableField("hours")
  private Integer hours;
  /** 参与抓取的频道数。 */
  @TableField("channel_count")
  private Integer channelCount;
  /** 抓取到的条目数。 */
  @TableField("item_count")
  private Integer itemCount;
  /** 新增条目数。 */
  @TableField("new_items")
  private Integer newItems;
  /** 去重条目数。 */
  @TableField("deduped_items")
  private Integer dedupedItems;
  /** 错误信息。 */
  @TableField("error_message")
  private String errorMessage;
  /** 异步任务 ID。 */
  @TableField("job_id")
  private String jobId;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 结束时间。 */
  @TableField("finished_at")
  private LocalDateTime finishedAt;
}
