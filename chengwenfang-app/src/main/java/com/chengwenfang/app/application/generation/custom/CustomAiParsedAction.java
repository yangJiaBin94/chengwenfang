package com.chengwenfang.app.application.generation.custom;

import lombok.Builder;
import lombok.Data;

/**
 * 自定义节点解析后的单条动作。
 */
@Data
@Builder
public class CustomAiParsedAction {
  /** 动作码，见 {@link com.chengwenfang.prompt.facade.constant.CustomAiNodeActions}。 */
  private String op;
  /** write_* 正文。 */
  private String content;
  /** read_prompt_pack 的 提示词包 Id。 */
  private String promptPackId;
}
