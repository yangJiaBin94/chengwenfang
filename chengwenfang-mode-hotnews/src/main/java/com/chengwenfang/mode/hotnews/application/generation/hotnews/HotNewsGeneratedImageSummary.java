package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点图节点产出的配图摘要。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsGeneratedImageSummary {
  /** 配图 ID。 */
  private String id;
  /** 类型：cover / inline。 */
  private String kind;
  /** 序号。 */
  private Integer sequenceNo;
}
