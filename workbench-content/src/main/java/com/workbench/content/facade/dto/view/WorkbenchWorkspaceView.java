package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作台工作区信息。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchWorkspaceView {
  /** 工作台名称。 */
  private String name;

  /** 模块名称。 */
  private String moduleName;

  /** 主题名称。 */
  private String topicName;

  /** 主题 slug。 */
  private String topicSlug;

  /** 模式 ID。 */
  private String modeId;

  /** 模式名称。 */
  private String modeName;

  /** 模式编码。 */
  private String modeCode;

  /** 主题 ID。 */
  private String themeId;

  /** 主题名称。 */
  private String themeName;

  /** 主题编码。 */
  private String themeCode;
}
