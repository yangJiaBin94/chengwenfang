package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点文章骨架标量行。 */
@TableName("hotnews_article_skeletons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsArticleSkeletonEntity {
  @TableId("topic_id")
  private String topicId;
  @TableField("raw_file_id")
  private String rawFileId;
  @TableField("working_title")
  private String workingTitle;
  @TableField("scope")
  private String scope;
  @TableField("core_thesis")
  private String coreThesis;
  @TableField("core_event")
  private String coreEvent;
  @TableField("narrative_shape")
  private String narrativeShape;
  @TableField("illustration_style")
  private String illustrationStyle;
  @TableField("cover_brief")
  private String coverBrief;
  @TableField("image_prompt_requirements")
  private String imagePromptRequirements;
  @TableField("recommended_word_count_option")
  private String recommendedWordCountOption;
  @TableField("recommended_image_count_option")
  private String recommendedImageCountOption;
  @TableField("updated_at_text")
  private String updatedAtText;
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
