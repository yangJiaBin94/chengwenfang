package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 主题相关性整包结果解析。 */
class ThemeRelevanceBatchParseTest {

  @Test
  void parseOffTopicIds_readsIds() {
    assertThat(RadarCollectAiSupport.parseOffTopicIds(
        "说明\n{\"offTopicIds\":[\"a\",\"b\"]}"))
        .containsExactly("a", "b");
  }

  @Test
  void parseOffTopicIds_blankMeansKeepAll() {
    assertThat(RadarCollectAiSupport.parseOffTopicIds("")).isEmpty();
    assertThat(RadarCollectAiSupport.parseOffTopicIds("不是 json")).isEmpty();
  }
}
