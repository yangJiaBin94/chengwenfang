package com.chengwenfang.prompt.facade;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.prompt.facade.constant.CoursePromptPackNames;
import com.chengwenfang.prompt.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.prompt.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流节点绑定用的 提示词包 id 解析（课程 scene 优先，永不绑热点同名行）。
 */
public final class PromptPackBindingIds {
  private PromptPackBindingIds() {}

  /**
   * 按名称解析课程相关 提示词包 id。
   *
   * @param all 全部未删除提示词包
   * @return 提示词包 名 → id
   */
  public static Map<String, String> resolveCoursePromptPackIds(List<PromptPackView> all) {
    Map<String, String> byName = new LinkedHashMap<>();
    List<String> wanted = List.of(
        CoursePromptPackNames.CHENGWENFANG_RULES,
        CoursePromptPackNames.COURSE_REVIEW,
        CoursePromptPackNames.IMAGE_GENERATION,
        CoursePromptPackNames.COURSE_DRAFT,
        CoursePromptPackNames.WECHAT_CONVERSION,
        CoursePromptPackNames.IMAGE_PLANNING,
        CoursePromptPackNames.FINALIZATION,
        CoursePromptPackNames.AUTHOR_STYLE);
    for (String name : wanted) {
      String id = pickCoursePromptPackId(all, name);
      if (id != null) {
        byName.put(name, id);
      }
    }
    return byName;
  }

  /**
   * 为课程工作流挑选 提示词包 id，排除热点 scene 同名行。
   * <p>同名时优先级：课程 scene → global/system → 其它非热点 scene。
   *
   * @param all  全部未删除提示词包
   * @param name 提示词包名称
   * @return 选中的 id；没有合适行则 null
   */
  public static String pickCoursePromptPackId(List<PromptPackView> all, String name) {
    if (all == null || StrUtil.isBlank(name)) {
      return null;
    }
    String courseScene = null;
    String globalOrSystem = null;
    String otherNonHotNews = null;
    for (PromptPackView promptPack : all) {
      if (promptPack == null || StrUtil.isBlank(promptPack.getId()) || !promptPackNameMatches(name, promptPack.getName())) {
        continue;
      }
      String scene = StrUtil.nullToEmpty(promptPack.getScene());
      if (HotNewsWorkflowKeys.SCENE.equals(scene)) {
        continue;
      }
      if (CourseWorkflowKeys.SCENE.equals(scene)) {
        courseScene = promptPack.getId();
        break;
      }
      String scope = StrUtil.nullToEmpty(promptPack.getScope());
      boolean globalLike = "global".equalsIgnoreCase(scene)
          || "system".equalsIgnoreCase(scope)
          || "global".equalsIgnoreCase(scope)
          || scene.isBlank();
      if (globalLike && globalOrSystem == null) {
        globalOrSystem = promptPack.getId();
      } else if (!globalLike && otherNonHotNews == null) {
        otherNonHotNews = promptPack.getId();
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
   * 按工作流 scene 解析绑定提示词包 id。
   *
   * @param all            全部 提示词包
   * @param promptPackName      绑定名
   * @param workflowScene  工作流 scene
   * @return 提示词包 id；找不到则 null
   */
  public static String resolveForWorkflowScene(
      List<PromptPackView> all, String promptPackName, String workflowScene) {
    if (CourseWorkflowKeys.SCENE.equals(workflowScene)) {
      return pickCoursePromptPackId(all, promptPackName);
    }
    if (all == null || StrUtil.isBlank(promptPackName)) {
      return null;
    }
    for (PromptPackView promptPack : all) {
      if (promptPack == null) {
        continue;
      }
      if (promptPackNameMatches(promptPackName, promptPack.getName())
          && workflowScene.equals(StrUtil.nullToEmpty(promptPack.getScene()))) {
        return promptPack.getId();
      }
    }
    return null;
  }

  /**
   * 判断库内 提示词包 名是否匹配目标名（含「工作台规则理解」→「成文坊规则理解」别名）。
   *
   * @param wanted   期望名称（通常为常量新名）
   * @param actual   库内名称
   * @return 是否视为同一 提示词包
   */
  static boolean promptPackNameMatches(String wanted, String actual) {
    if (StrUtil.isBlank(wanted) || StrUtil.isBlank(actual)) {
      return false;
    }
    if (wanted.equals(actual)) {
      return true;
    }
    // 课程规则 提示词包：新旧名互通，避免迁移窗口内绑定失败
    return CoursePromptPackNames.CHENGWENFANG_RULES.equals(wanted)
        && CoursePromptPackNames.LEGACY_RULES.equals(actual);
  }
}
