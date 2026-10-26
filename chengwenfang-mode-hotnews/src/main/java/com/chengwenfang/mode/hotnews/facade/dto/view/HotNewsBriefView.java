package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题简报视图（对齐前端 HotNewsBrief）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsBriefView {
  /** 简报 ID */
  private String id;
  /** 选题 ID */
  private String topicId;
  /** 状态 */
  private String status;
  /** 标题 */
  private String title;
  /** 文章类型 */
  private String articleType;
  /** 目标受众 */
  private String targetAudience;
  /** 写作角度 */
  private String angle;
  /** 摘要结构化内容 */
  private Map<String, Object> summary;
  /** 来源快照 */
  private List<HotNewsBriefSourceRow> sourceSnapshot;
  /** 确认备注 */
  private String confirmationNote;
  /** 创建时间 */
  private LocalDateTime createdAt;
  /** 更新时间 */
  private LocalDateTime updatedAt;
  /** 确认时间 */
  private LocalDateTime confirmedAt;
}
