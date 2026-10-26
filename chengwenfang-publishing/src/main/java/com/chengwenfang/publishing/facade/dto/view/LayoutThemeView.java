package com.chengwenfang.publishing.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 排版主题视图（对齐前端 LayoutThemeData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutThemeView {
  /** 主题 ID。 */
  private String id;
  /** 主题 key。 */
  private String key;
  /** 名称。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 类型 system / custom。 */
  private String type;
  /** 状态 enabled / disabled。 */
  private String status;
  /** 当前版本 ID。 */
  private String currentVersionId;
  /** 版本号。 */
  private int versionNo;
  /** 主题配置。 */
  private Map<String, Object> config;
  /** 更新时间。 */
  private String updatedAt;
}