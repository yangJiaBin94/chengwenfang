package com.chengwenfang.prompt.facade;

import com.chengwenfang.prompt.facade.dto.view.BuiltinInspectView;

/**
 * 内置提示词包 / 工作流体检门面（只读）。
 */
public interface BuiltinConfigFacade {

  /**
   * 检查库内内置提示词包 与三条主工作流是否就绪。
   *
   * @return 体检视图
   */
  BuiltinInspectView inspect();
}
