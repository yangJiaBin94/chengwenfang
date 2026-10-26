package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题与信源条目的关联（证据链）。 */
@TableName("hotnews_topic_sources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicSourceEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 选题/话题 ID。 */
  @TableField("topic_id")
  private String topicId;
  /** 信源条目 ID。 */
  @TableField("source_item_id")
  private String sourceItemId;
  /** 关联角色，默认 evidence。 */
  @TableField("role")
  private String role;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
