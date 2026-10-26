package com.chengwenfang.skill.application;

import com.chengwenfang.skill.facade.SkillBindingIds;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import java.util.List;
import java.util.Map;

/**
 * 内置工作流相关的 Skill 名 → id 解析（委托 {@link SkillBindingIds}）。
 * <p>定义落库与规范图覆盖由 {@link BuiltinWorkflowDefinitionService} 负责；本类只提供 Skill 选择规则。
 * <p>工作流与 Skill 的首次灌库由部署脚本完成；本类不再在启动时写库。
 */
public final class BuiltinWorkflowSeeder {
  private BuiltinWorkflowSeeder() {}

  /**
   * 按名称解析课程相关 Skill id。
   *
   * @param all 全部未删除 Skill
   * @return Skill 名 → id
   */
  public static Map<String, String> resolveCourseSkillIds(List<SkillView> all) {
    return SkillBindingIds.resolveCourseSkillIds(all);
  }

  /**
   * 为课程工作流挑选 Skill id，排除热点 scene 同名行。
   *
   * @param all  全部未删除 Skill
   * @param name Skill 名称
   * @return 选中的 id；没有合适行则 null
   */
  public static String pickCourseSkillId(List<SkillView> all, String name) {
    return SkillBindingIds.pickCourseSkillId(all, name);
  }
}
