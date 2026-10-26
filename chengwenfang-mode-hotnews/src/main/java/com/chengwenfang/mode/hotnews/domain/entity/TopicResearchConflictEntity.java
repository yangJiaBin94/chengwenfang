package com.chengwenfang.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题调研冲突：同题多源标题/主张冲突提示。 */
@TableName("hotnews_research_conflicts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicResearchConflictEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 研究/采集运行 ID。 */
  @TableField("run_id")
  private String runId;
  /** 选题/话题 ID。 */
  @TableField("topic_id")
  private String topicId;
  /** 冲突主张摘要。 */
  @TableField("claim")
  private String claim;
  /** 冲突细节说明。 */
  @TableField("detail")
  private String detail;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
