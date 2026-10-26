package com.workbench.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 作品生成设置（课程字数/配图与热点参数）。 */
@TableName("work_generation_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkGenerationSettingsEntity {
  /** 作品 ID。 */
  @TableId("work_id")
  private String workId;
  /** 用户选定字数档位。 */
  @TableField("word_count_option")
  private String wordCountOption;
  /** 用户选定配图档位。 */
  @TableField("image_count_option")
  private String imageCountOption;
  /** 系统推荐字数档位。 */
  @TableField("recommended_word_count_option")
  private String recommendedWordCountOption;
  /** 系统推荐配图档位。 */
  @TableField("recommended_image_count_option")
  private String recommendedImageCountOption;
  /** 推荐理由。 */
  @TableField("recommendation_reason")
  private String recommendationReason;
  /** 推荐生成时间。 */
  @TableField("recommended_at")
  private LocalDateTime recommendedAt;
  /** 推荐结果指纹。 */
  @TableField("preference_fingerprint")
  private String preferenceFingerprint;
  /** 热点：工作标题。 */
  @TableField("working_title")
  private String workingTitle;
  /** 热点：插画风格。 */
  @TableField("illustration_style")
  private String illustrationStyle;
  /** 热点：封面说明。 */
  @TableField("cover_brief")
  private String coverBrief;
  /** 热点：配图提示词要求。 */
  @TableField("image_prompt_requirements")
  private String imagePromptRequirements;
  /** 热点：最近改写指令。 */
  @TableField("last_rewrite_instruction")
  private String lastRewriteInstruction;
  /** 热点：改写时间字符串。 */
  @TableField("rewritten_at")
  private String rewrittenAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
