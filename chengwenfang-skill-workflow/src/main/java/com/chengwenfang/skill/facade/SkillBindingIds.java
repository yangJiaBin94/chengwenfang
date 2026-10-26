package com.chengwenfang.skill.facade;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.skill.facade.constant.CourseSkillNames;
import com.chengwenfang.skill.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.skill.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流节点绑定用的 Skill id 解析（课程 scene 优先，永不绑热点同名行）。
 */
public final class SkillBindingIds {
  private SkillBindingIds() {}

  /**
   * 按名称解析课程相关 Skill id。
   *
   * @param all 全部未删除 Skill
   * @return Skill 名 → id
   */
  public static Map<String, String> resolveCourseSkillIds(List<SkillView> all) {
    Map<String, String> byName = new LinkedHashMap<>();
    List<String> wanted = List.of(
        CourseSkillNames.CHENGWENFANG_RULES,
        CourseSkillNames.COURSE_REVIEW,
        CourseSkillNames.IMAGE_GENERATION,
        CourseSkillNames.COURSE_DRAFT,
        CourseSkillNames.WECHAT_CONVERSION,
        CourseSkillNames.IMAGE_PLANNING,
        CourseSkillNames.FINALIZATION,
        CourseSkillNames.AUTHOR_STYLE);
    for (String name : wanted) {
      String id = pickCourseSkillId(all, name);
      if (id != null) {
        byName.put(name, id);
      }
    }
    return byName;
  }

  /**
   * 为课程工作流挑选 Skill id，排除热点 scene 同名行。
   * <p>同名时优先级：课程 scene → global/system → 其它非热点 scene。
   *
   * @param all  全部未删除 Skill
   * @param name Skill 名称
   * @return 选中的 id；没有合适行则 null
   */
  public static String pickCourseSkillId(List<SkillView> all, String name) {
    if (all == null || StrUtil.isBlank(name)) {
      return null;
    }
    String courseScene = null;
    String globalOrSystem = null;
    String otherNonHotNews = null;
    for (SkillView skill : all) {
      if (skill == null || !name.equals(skill.getName()) || StrUtil.isBlank(skill.getId())) {
        continue;
      }
      String scene = StrUtil.nullToEmpty(skill.getScene());
      if (HotNewsWorkflowKeys.SCENE.equals(scene)) {
        continue;
      }
      if (CourseWorkflowKeys.SCENE.equals(scene)) {
        courseScene = skill.getId();
        break;
      }
      String scope = StrUtil.nullToEmpty(skill.getScope());
      boolean globalLike = "global".equalsIgnoreCase(scene)
          || "system".equalsIgnoreCase(scope)
          || "global".equalsIgnoreCase(scope)
          || scene.isBlank();
      if (globalLike && globalOrSystem == null) {
        globalOrSystem = skill.getId();
      } else if (!globalLike && otherNonHotNews == null) {
        otherNonHotNews = skill.getId();
      }
    }
    if (courseScene != null) {
      return courseScene;
    }
    if (globalOrSystem != null) {
      return globalOrSystem;
    }
    return otherNonHotNews;
  }

  /**
   * 按工作流 scene 解析绑定 Skill id。
   *
   * @param all            全部 Skill
   * @param skillName      绑定名
   * @param workflowScene  工作流 scene
   * @return Skill id；找不到则 null
   */
  public static String resolveForWorkflowScene(
      List<SkillView> all, String skillName, String workflowScene) {
    if (CourseWorkflowKeys.SCENE.equals(workflowScene)) {
      return pickCourseSkillId(all, skillName);
    }
    if (all == null || StrUtil.isBlank(skillName)) {
      return null;
    }
    for (SkillView skill : all) {
      if (skill == null) {
        continue;
      }
      if (skillName.equals(skill.getName())
          && workflowScene.equals(StrUtil.nullToEmpty(skill.getScene()))) {
        return skill.getId();
      }
    }
    return null;
  }
}
