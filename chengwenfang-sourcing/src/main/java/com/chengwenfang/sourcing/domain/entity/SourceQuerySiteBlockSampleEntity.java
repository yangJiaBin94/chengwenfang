package com.chengwenfang.sourcing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 搜索词站点拉黑的样例标题。 */
@TableName("source_query_site_block_samples")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceQuerySiteBlockSampleEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 拉黑记录 ID。 */
  @TableField("block_id")
  private String blockId;
  /** 顺序。 */
  @TableField("seq")
  private Integer seq;
  /** 样例标题。 */
  @TableField("title")
  private String title;
}
