package com.workbench.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link ArticlePublishDateSource} 键与解析。 */
class ArticlePublishDateSourceTest {

  @Test
  void from_recognizesKnownKeys() {
    assertThat(ArticlePublishDateSource.from("channel")).isEqualTo(ArticlePublishDateSource.CHANNEL);
    assertThat(ArticlePublishDateSource.from("ai_content"))
        .isEqualTo(ArticlePublishDateSource.AI_CONTENT);
    assertThat(ArticlePublishDateSource.from("ai_html")).isEqualTo(ArticlePublishDateSource.AI_HTML);
    assertThat(ArticlePublishDateSource.from("metadata"))
        .isEqualTo(ArticlePublishDateSource.METADATA);
    assertThat(ArticlePublishDateSource.from("no_content"))
        .isEqualTo(ArticlePublishDateSource.NO_CONTENT);
  }

  @Test
  void from_blankFallsBackToNone() {
    assertThat(ArticlePublishDateSource.from(null)).isEqualTo(ArticlePublishDateSource.NONE);
    assertThat(ArticlePublishDateSource.from(" ")).isEqualTo(ArticlePublishDateSource.NONE);
    assertThat(ArticlePublishDateSource.from("unknown")).isEqualTo(ArticlePublishDateSource.NONE);
  }

  @Test
  void resolved_onlyWhenDateObtained() {
    assertThat(ArticlePublishDateSource.CHANNEL.isResolved()).isTrue();
    assertThat(ArticlePublishDateSource.AI_CONTENT.isResolved()).isTrue();
    assertThat(ArticlePublishDateSource.AI_HTML.isResolved()).isTrue();
    assertThat(ArticlePublishDateSource.METADATA.isResolved()).isTrue();
    assertThat(ArticlePublishDateSource.NONE.isResolved()).isFalse();
    assertThat(ArticlePublishDateSource.NO_CONTENT.isResolved()).isFalse();
  }
}
