package com.chengwenfang.mode.hotnews.facade.constant;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.prompt.facade.enums.HotNewsWorkflowNodeType;
import org.junit.jupiter.api.Test;

/** {@link HotNewsGenerationScopes} images / wechat 短路。 */
class HotNewsGenerationScopesTest {

  @Test
  void images_scope_skips_draft_and_keeps_image_nodes() {
    assertThat(HotNewsGenerationScopes.isImages("images")).isTrue();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.IMAGES, HotNewsWorkflowNodeType.GENERATE_DRAFT))
        .isFalse();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.IMAGES, HotNewsWorkflowNodeType.PREPARE_FACTS))
        .isFalse();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.IMAGES, HotNewsWorkflowNodeType.GENERATE_IMAGES))
        .isTrue();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.IMAGES, HotNewsWorkflowNodeType.COMMIT_VERSION))
        .isTrue();
  }

  @Test
  void wechat_scope_skips_draft_images_and_keeps_wechat_nodes() {
    assertThat(HotNewsGenerationScopes.isWechat("wechat")).isTrue();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.WECHAT, HotNewsWorkflowNodeType.GENERATE_DRAFT))
        .isFalse();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.WECHAT, HotNewsWorkflowNodeType.GENERATE_IMAGES))
        .isFalse();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.WECHAT, HotNewsWorkflowNodeType.ATTACH_EVIDENCE))
        .isTrue();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.WECHAT, HotNewsWorkflowNodeType.GENERATE_WECHAT))
        .isTrue();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.WECHAT, HotNewsWorkflowNodeType.REVIEW_WECHAT))
        .isTrue();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.WECHAT, HotNewsWorkflowNodeType.COMMIT_VERSION))
        .isTrue();
  }

  @Test
  void images_scope_skips_attach_evidence() {
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.IMAGES, HotNewsWorkflowNodeType.ATTACH_EVIDENCE))
        .isFalse();
  }

  @Test
  void full_scope_runs_all_nodes() {
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.FULL, HotNewsWorkflowNodeType.GENERATE_DRAFT))
        .isTrue();
    assertThat(HotNewsGenerationScopes.shouldExecute(
            HotNewsGenerationScopes.FULL, HotNewsWorkflowNodeType.ATTACH_EVIDENCE))
        .isTrue();
    assertThat(HotNewsGenerationScopes.normalize(null)).isEqualTo(HotNewsGenerationScopes.FULL);
  }
}
