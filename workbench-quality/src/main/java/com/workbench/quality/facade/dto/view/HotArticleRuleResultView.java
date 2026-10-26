package com.workbench.quality.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点文章规则审稿结果（包装引擎字段）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleRuleResultView {
  /** 规则码。 */
  private String code;
  /** 状态。 */
  private String status;
  /** 是否阻断。 */
  private Boolean blocking;
  /** 详情。 */
  private String detail;
  /** 标题。 */
  private String title;
  /** 扩展字段。 */
  private Map<String, Object> extras;
}