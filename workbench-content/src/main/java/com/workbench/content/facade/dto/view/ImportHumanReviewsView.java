package com.workbench.content.facade.dto.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 人工审核导入结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportHumanReviewsView {
  /** 导入数量。 */
  private int imported;

  /** 已迁移键。 */
  private List<String> migratedKeys;

  /** 跳过键。 */
  private List<String> skippedKeys;

  /** 是否成功。 */
  private boolean ok;
}
