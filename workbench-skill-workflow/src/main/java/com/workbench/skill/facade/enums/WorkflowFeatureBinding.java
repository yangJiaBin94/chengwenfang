package com.workbench.skill.facade.enums;

import cn.hutool.core.util.StrUtil;
import com.workbench.skill.facade.constant.CourseWorkflowKeys;
import com.workbench.skill.facade.constant.HotNewsRadarCollectWorkflowKeys;
import com.workbench.skill.facade.constant.HotNewsWorkflowKeys;
import java.util.Locale;

/**
 * 功能按钮/启动接口与内置工作流的闭集绑定（功能键 → scene / definitionKey）。
 */
public enum WorkflowFeatureBinding {
  /** 热点雷达「开始采集」。 */
  HOT_NEWS_RADAR_COLLECT(
      HotNewsRadarCollectWorkflowKeys.SCENE,
      HotNewsRadarCollectWorkflowKeys.SCENE,
      HotNewsRadarCollectWorkflowKeys.DEFINITION_KEY,
      HotNewsRadarCollectWorkflowKeys.DISPLAY_NAME),
  /** 热点文章工作区「开始生成」。 */
  HOT_NEWS_ARTICLE_GENERATION(
      HotNewsWorkflowKeys.SCENE,
      HotNewsWorkflowKeys.SCENE,
      HotNewsWorkflowKeys.DEFINITION_KEY,
      HotNewsWorkflowKeys.DISPLAY_NAME),
  /** 课程体系正式内容生成。 */
  COURSE_CONTENT_GENERATION(
      CourseWorkflowKeys.SCENE,
      CourseWorkflowKeys.SCENE,
      CourseWorkflowKeys.DEFINITION_KEY,
      CourseWorkflowKeys.DISPLAY_NAME);

  private final String featureKey;
  private final String scene;
  private final String definitionKey;
  private final String displayName;

  WorkflowFeatureBinding(
      String featureKey, String scene, String definitionKey, String displayName) {
    this.featureKey = featureKey;
    this.scene = scene;
    this.definitionKey = definitionKey;
    this.displayName = displayName;
  }

  /** @return 前端/接口功能键 */
  public String getFeatureKey() {
    return featureKey;
  }

  /** @return 工作流场景码 */
  public String getScene() {
    return scene;
  }

  /** @return 工作流定义 key */
  public String getDefinitionKey() {
    return definitionKey;
  }

  /** @return 用户可见工作流名 */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * 按功能键解析绑定；未知则 null。
   *
   * @param featureKey 功能键
   * @return 绑定；未匹配返回 null
   */
  public static WorkflowFeatureBinding fromFeatureKey(String featureKey) {
    if (StrUtil.isBlank(featureKey)) {
      return null;
    }
    String key = featureKey.trim().toLowerCase(Locale.ROOT);
    for (WorkflowFeatureBinding binding : values()) {
      if (binding.featureKey.equals(key)) {
        return binding;
      }
    }
    return null;
  }

  /**
   * 按场景码解析绑定；未知则 null。
   *
   * @param scene 场景码
   * @return 绑定；未匹配返回 null
   */
  public static WorkflowFeatureBinding fromScene(String scene) {
    if (StrUtil.isBlank(scene)) {
      return null;
    }
    String key = scene.trim().toLowerCase(Locale.ROOT);
    for (WorkflowFeatureBinding binding : values()) {
      if (binding.scene.equals(key)) {
        return binding;
      }
    }
    return null;
  }

  /**
   * 无可用工作流时的中文提示。
   *
   * @return 提示文案
   */
  public String unavailableMessage() {
    return "「" + displayName + "」工作流已停用或未发布，请先在工作流中心启用。";
  }
}
