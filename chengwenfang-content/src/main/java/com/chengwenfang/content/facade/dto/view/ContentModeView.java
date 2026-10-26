package com.chengwenfang.content.facade.dto.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 教学模式视图（对齐前端 ContentModeData）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentModeView {
  /** 模式 ID。 */
  private String id;

  /** 模式编码。 */
  private String code;

  /** 名称。 */
  private String name;

  /** 描述。 */
  private String description;

  /** 状态。 */
  private String status;

  /** 主题列表。 */
  private List<ContentThemeView> themes;
}
