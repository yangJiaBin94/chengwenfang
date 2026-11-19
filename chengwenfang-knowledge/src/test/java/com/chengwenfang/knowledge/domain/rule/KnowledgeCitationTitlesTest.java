package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link KnowledgeCitationTitles} 单元测试。 */
class KnowledgeCitationTitlesTest {

  @Test
  void replacesTechnicalCourseIdWithHeading() {
    String title = KnowledgeCitationTitles.resolve(
        "course-ai_self_study_zero-a00375d2-1 原稿",
        "AI 的定义与边界 > 本课小结");
    assertThat(title).isEqualTo("AI 的定义与边界 · 原稿");
  }

  @Test
  void keepsHumanTitle() {
    String title = KnowledgeCitationTitles.resolve(
        "零基础认识 AI 原稿",
        "开场 > 目标");
    assertThat(title).isEqualTo("零基础认识 AI · 原稿");
  }
}
