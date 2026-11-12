package com.chengwenfang.prompt.application;

import com.chengwenfang.prompt.facade.PromptPackBindingIds;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import java.util.List;
import java.util.Map;

/**
 * 内置工作流相关的 提示词包 名 → id 解析（委托 {@link PromptPackBindingIds}）。
 * <p>定义落库与规范图覆盖由 {@link BuiltinWorkflowDefinitionService} 负责；本类只提供 提示词包 选择规则。
 * <p>工作流与 提示词包 的首次灌库由部署脚本完成；本类不再在启动时写库。
 */
public final class BuiltinWorkflowSeeder {
  private BuiltinWorkflowSeeder() {}

  /**
   * 按名称解析课程相关 提示词包 id。
   *
   * @param all 全部未删除提示词包
   * @return 提示词包 名 → id
   */
  public static Map<String, String> resolveCoursePromptPackIds(List<PromptPackView> all) {
    return PromptPackBindingIds.resolveCoursePromptPackIds(all);
  }

  /**
   * 为课程工作流挑选 提示词包 id，排除热点 scene 同名行。
   *
   * @param all  全部未删除提示词包
   * @param name 提示词包名称
   * @return 选中的 id；没有合适行则 null
   */
  public static String pickCoursePromptPackId(List<PromptPackView> all, String name) {
    return PromptPackBindingIds.pickCoursePromptPackId(all, name);
  }
}
