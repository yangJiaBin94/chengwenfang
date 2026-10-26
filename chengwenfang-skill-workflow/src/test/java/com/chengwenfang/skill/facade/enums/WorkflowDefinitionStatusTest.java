package com.chengwenfang.skill.facade.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link WorkflowDefinitionStatus} 可运行态判定。 */
class WorkflowDefinitionStatusTest {

  @Test
  void only_active_is_runnable() {
    assertThat(WorkflowDefinitionStatus.ACTIVE.isRunnable()).isTrue();
    assertThat(WorkflowDefinitionStatus.PUBLISHED.isRunnable()).isFalse();
  }

  @Test
  void inactive_archived_draft_are_not_runnable() {
    assertThat(WorkflowDefinitionStatus.INACTIVE.isRunnable()).isFalse();
    assertThat(WorkflowDefinitionStatus.ARCHIVED.isRunnable()).isFalse();
    assertThat(WorkflowDefinitionStatus.DRAFT.isRunnable()).isFalse();
  }

  @Test
  void afterPublish_keeps_enable_switch() {
    assertThat(WorkflowDefinitionStatus.afterPublish(WorkflowDefinitionStatus.ACTIVE))
        .isEqualTo(WorkflowDefinitionStatus.ACTIVE);
    assertThat(WorkflowDefinitionStatus.afterPublish(WorkflowDefinitionStatus.INACTIVE))
        .isEqualTo(WorkflowDefinitionStatus.INACTIVE);
    assertThat(WorkflowDefinitionStatus.afterPublish(WorkflowDefinitionStatus.ARCHIVED))
        .isEqualTo(WorkflowDefinitionStatus.ARCHIVED);
    assertThat(WorkflowDefinitionStatus.afterPublish(WorkflowDefinitionStatus.DRAFT))
        .isEqualTo(WorkflowDefinitionStatus.INACTIVE);
    assertThat(WorkflowDefinitionStatus.afterPublish(WorkflowDefinitionStatus.PUBLISHED))
        .isEqualTo(WorkflowDefinitionStatus.INACTIVE);
  }

  @Test
  void from_parses_known_values() {
    assertThat(WorkflowDefinitionStatus.from("active"))
        .isEqualTo(WorkflowDefinitionStatus.ACTIVE);
    assertThat(WorkflowDefinitionStatus.from("inactive"))
        .isEqualTo(WorkflowDefinitionStatus.INACTIVE);
    assertThat(WorkflowDefinitionStatus.from("published"))
        .isEqualTo(WorkflowDefinitionStatus.PUBLISHED);
    assertThat(WorkflowDefinitionStatus.from("")).isEqualTo(WorkflowDefinitionStatus.DRAFT);
    assertThat(WorkflowDefinitionStatus.from(null)).isEqualTo(WorkflowDefinitionStatus.DRAFT);
  }
}
