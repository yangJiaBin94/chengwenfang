package com.workbench.mode.hotnews.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点模式作品扩展（原热点 article 壳）。 */
@TableName("hotnews_works")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotnewsWorkEntity {
  /** 作品 ID（= works.id = 原 articleId）。 */
  @TableId("work_id")
  private String workId;
  /** 选题 ID。 */
  @TableField("topic_id")
  private String topicId;
  /** 来源简报 ID。 */
  @TableField("brief_id")
  private String briefId;
  /** draft / generating / ready / rewritten。 */
  @TableField("status")
  private String status;
}
