package com.chengwenfang.content.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 内容主题视图（对齐前端 ContentThemeData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentThemeView {
  /** 主题 ID。 */
  private String id;

  /** 所属模式 ID。 */
  private String modeId;

  /** 模式名称（解析时填充）。 */
  private String modeName;

  /** 模式编码（解析时填充）。 */
  private String modeCode;

  /** 主题编码。 */
  private String code;

  /** 名称。 */
  private String name;

  /** 描述。 */
  private String description;

  /** 状态。 */
  private String status;

  /** 系列数量。 */
  private int seriesCount;

  /** 课程数量。 */
  private int courseCount;

  /** 已完成数量。 */
  private int completedCount;

  /** 条目数量（热点）。 */
  private Integer itemCount;

  /** 候选数量（热点）。 */
  private Integer candidateCount;

  /** 创建来源：seed / wizard。 */
  private String createdVia;

  /** 问卷 briefing JSON 字符串。 */
  private String briefingJson;

  /** 课表草案 JSON 字符串。 */
  private String curriculumDraftJson;

  /** 路线说明。 */
  private String routeNarrative;

  /** 默认生成偏好 JSON。 */
  private String defaultsJson;

  /** 最近课表生成 jobId。 */
  private String generateJobId;

  /** 软删除时间；空表示未删除。 */
  private LocalDateTime deletedAt;
}
