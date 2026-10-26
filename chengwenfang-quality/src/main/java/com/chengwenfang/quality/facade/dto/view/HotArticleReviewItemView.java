package com.chengwenfang.quality.facade.dto.view;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点审稿条目视图（对齐前端 HotNewsAudit.items）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleReviewItemView {
  /** 条目 ID（通常等于 code） */
  private String id;
  /** 规则码 */
  private String code;
  /** 引擎 */
  private String engine;
  /** 分类 */
  private String category;
  /** 严重级别 */
  private String severity;
  /** 标题 */
  private String title;
  /** 详情 */
  private String detail;
  /** 位置 */
  private String location;
  /** 多位置 */
  private List<Map<String, Object>> locations;
  /** 证据 */
  private List<Map<String, Object>> evidence;
  /** 是否可修复 */
  private Boolean repairable;
  /** 是否需人工确认 */
  private Boolean requiresHumanConfirmation;
  /** 修复状态 */
  private String repairStatus;
  /** 修复指令 */
  private String repairInstruction;
  /** 是否阻断 */
  private Boolean blocking;
  /** 状态 */
  private String status;
  /** 豁免原因 */
  private String waiverReason;
}
