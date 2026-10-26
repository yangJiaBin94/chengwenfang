package com.chengwenfang.sourcing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 抓取运行中单个频道的子运行记录。 */
@TableName("source_fetch_channel_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceFetchChannelRunEntity {
  @TableId("id")
  private String id;
  @TableField("fetch_run_id")
  private String fetchRunId;
  @TableField("channel_id")
  private String channelId;
  @TableField("status")
  private String status;
  @TableField("items_count")
  private Integer itemsCount;
  @TableField("new_items")
  private Integer newItems;
  @TableField("error_code")
  private String errorCode;
  @TableField("error_message")
  private String errorMessage;
  @TableField("started_at")
  private LocalDateTime startedAt;
  @TableField("finished_at")
  private LocalDateTime finishedAt;
}
