package com.chengwenfang.skill.facade.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.skill.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.skill.facade.constant.HotNewsRadarCollectWorkflowKeys;
import com.chengwenfang.skill.facade.constant.HotNewsWorkflowKeys;
import org.junit.jupiter.api.Test;

/** {@link WorkflowFeatureBinding} 功能键 / 场景反查与提示文案。 */
class WorkflowFeatureBindingTest {

  @Test
  void fromFeatureKey_resolves_known_bindings() {
    assertThat(WorkflowFeatureBinding.fromFeatureKey(
            HotNewsRadarCollectWorkflowKeys.SCENE))
        .isEqualTo(WorkflowFeatureBinding.HOT_NEWS_RADAR_COLLECT);
    assertThat(WorkflowFeatureBinding.fromFeatureKey(
            HotNewsWorkflowKeys.SCENE))
        .isEqualTo(WorkflowFeatureBinding.HOT_NEWS_ARTICLE_GENERATION);
    assertThat(WorkflowFeatureBinding.fromFeatureKey(
            CourseWorkflowKeys.SCENE))
        .isEqualTo(WorkflowFeatureBinding.COURSE_CONTENT_GENERATION);
  }

  @Test
  void fromFeatureKey_unknown_returns_null() {
    assertThat(WorkflowFeatureBinding.fromFeatureKey("unknown_feature")).isNull();
    assertThat(WorkflowFeatureBinding.fromFeatureKey("")).isNull();
    assertThat(WorkflowFeatureBinding.fromFeatureKey(null)).isNull();
  }

  @Test
  void fromScene_matches_feature_key() {
    assertThat(WorkflowFeatureBinding.fromScene(HotNewsRadarCollectWorkflowKeys.SCENE))
        .isEqualTo(WorkflowFeatureBinding.HOT_NEWS_RADAR_COLLECT);
    assertThat(WorkflowFeatureBinding.fromScene("no_such_scene")).isNull();
  }

  @Test
  void unavailableMessage_mentions_display_name() {
    String message = WorkflowFeatureBinding.HOT_NEWS_RADAR_COLLECT.unavailableMessage();
    assertThat(message).contains(HotNewsRadarCollectWorkflowKeys.DISPLAY_NAME);
    assertThat(message).contains("停用").contains("工作流中心");
  }

  @Test
  void definitionKeys_align_with_constants() {
    assertThat(WorkflowFeatureBinding.HOT_NEWS_RADAR_COLLECT.getDefinitionKey())
        .isEqualTo(HotNewsRadarCollectWorkflowKeys.DEFINITION_KEY);
    assertThat(WorkflowFeatureBinding.HOT_NEWS_ARTICLE_GENERATION.getDefinitionKey())
        .isEqualTo(HotNewsWorkflowKeys.DEFINITION_KEY);
    assertThat(WorkflowFeatureBinding.COURSE_CONTENT_GENERATION.getDefinitionKey())
        .isEqualTo(CourseWorkflowKeys.DEFINITION_KEY);
  }
}
