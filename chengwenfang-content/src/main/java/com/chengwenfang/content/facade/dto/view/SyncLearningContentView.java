package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 学习内容同步结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLearningContentView {
  /** 是否成功。 */
  private boolean ok;

  /** 课程数量。 */
  private int courses;

  /** 媒资数量。 */
  private int media;

  /** 索引路径。 */
  private String indexPath;
}
