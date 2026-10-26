package com.chengwenfang.content.facade.dto.view;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文章修复结果（对齐前端 RepairResponseData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairResponseView {
  /** 修复运行 ID。 */
  private String repairRunId;
  /** 状态。 */
  private String status;
  /** 修复后文章。 */
  private ArticleView article;
  /** 修复前版本 ID。 */
  private String beforeVersionId;
  /** 修复后版本 ID。 */
  private String afterVersionId;
  /** 修复前审核（AuditResultView 序列化形态）。 */
  private Map<String, Object> beforeAudit;
  /** 修复后审核（AuditResultView 序列化形态）。 */
  private Map<String, Object> afterAudit;
  /** 修复前分数。 */
  private Integer beforeScore;
  /** 修复后分数。 */
  private Integer afterScore;
  /** 已解决规则码。 */
  private List<String> resolvedCodes;
  /** 新增规则码。 */
  private List<String> addedCodes;
  /** 创建时间。 */
  private String createdAt;
  /** 完成时间。 */
  private String finishedAt;
}
