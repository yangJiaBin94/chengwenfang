package com.chengwenfang.mode.hotnews.facade.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link HotNewsTopicOriginKinds} 单元测试。 */
class HotNewsTopicOriginKindsTest {

  @Test
  void isAiThemeSeed_matchesConstantOnly() {
    assertThat(HotNewsTopicOriginKinds.isAiThemeSeed(HotNewsTopicOriginKinds.AI_THEME_SEED))
        .isTrue();
    assertThat(HotNewsTopicOriginKinds.isAiThemeSeed("searxng")).isFalse();
    assertThat(HotNewsTopicOriginKinds.isAiThemeSeed(null)).isFalse();
    assertThat(HotNewsTopicOriginKinds.isAiThemeSeed("")).isFalse();
  }
}
