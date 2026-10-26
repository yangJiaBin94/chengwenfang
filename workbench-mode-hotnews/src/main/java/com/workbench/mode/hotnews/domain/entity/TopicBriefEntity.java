package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题写作简报（结构化列；列表与来源在子表）。 */
@TableName("hotnews_briefs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicBriefEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 选题/话题 ID。 */
  @TableField("topic_id")
  private String topicId;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** draft / confirmed。 */
  @TableField("status")
  private String status;
  /** 简报标题。 */
  @TableField("title")
  private String title;
  /** 概览。 */
  @TableField("overview")
  private String overview;
  /** 核心论点。 */
  @TableField("core_thesis")
  private String coreThesis;
  /** 核心事件。 */
  @TableField("core_event")
  private String coreEvent;
  /** 叙事形态。 */
  @TableField("narrative_shape")
  private String narrativeShape;
  /** 确认备注。 */
  @TableField("confirmation_note")
  private String confirmationNote;
  /** 确认时间。 */
  @TableField("confirmed_at")
  private LocalDateTime confirmedAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
