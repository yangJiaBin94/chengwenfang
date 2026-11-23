package com.chengwenfang.mode.hotnews.application.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.ai.facade.dto.view.WebSearchCitation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link WebSearchCitationIndex} citation 闸门。 */
class WebSearchCitationIndexTest {

  @Test
  void resolve_accepts_url_in_citations() {
    WebSearchCitationIndex index = WebSearchCitationIndex.from(List.of(
        WebSearchCitation.builder()
            .url("https://Example.com/news/a/")
            .title("示例新闻")
            .build()));
    assertThat(index.size()).isEqualTo(1);
    assertThat(index.resolve("https://example.com/news/a"))
        .isPresent()
        .get()
        .satisfies(resolved -> {
          assertThat(resolved.url()).isEqualTo("https://Example.com/news/a/");
          assertThat(resolved.title()).isEqualTo("示例新闻");
        });
  }

  @Test
  void resolve_rejects_url_not_in_citations() {
    WebSearchCitationIndex index = WebSearchCitationIndex.from(List.of(
        WebSearchCitation.builder().url("https://a.example/x").title("A").build()));
    assertThat(index.resolve("https://b.example/y")).isEmpty();
    assertThat(index.titleOf("https://b.example/y")).isEmpty();
  }
}
