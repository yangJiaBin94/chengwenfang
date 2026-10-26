package com.workbench.app.application.generation.custom;

import lombok.Builder;
import lombok.Data;

/**
 * 自定义节点解析后的单条动作。
 */
@Data
@Builder
public class CustomAiParsedAction {
  /** 动作码，见 {@link com.workbench.skill.facade.constant.CustomAiNodeActions}。 */
  private String op;
  /** write_* 正文。 */
  private String content;
  /** read_skill 的 Skill Id。 */
  private String skillId;
}
