package com.workbench.skill.facade;

import com.workbench.skill.facade.dto.view.BuiltinInspectView;

/**
 * 内置 Skill / 工作流体检门面（只读）。
 */
public interface BuiltinConfigFacade {

  /**
   * 检查库内内置 Skill 与三条主工作流是否就绪。
   *
   * @return 体检视图
   */
  BuiltinInspectView inspect();
}
