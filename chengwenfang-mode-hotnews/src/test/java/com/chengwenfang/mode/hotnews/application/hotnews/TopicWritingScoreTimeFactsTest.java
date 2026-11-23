package com.chengwenfang.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link TopicWritingScoreTimeFacts} 时间事实组装。 */
class TopicWritingScoreTimeFactsTest {

  @Test
  void buildBlock_includes_now_and_unknown_material_time() {
    String block = TopicWritingScoreTimeFacts.buildBlock(
        LocalDateTime.of(2026, 11, 20, 16, 5),
        List.of(),
        null,
        null);
    assertThat(block).contains("当前时间：2026-11-20 16:05");
    assertThat(block).contains("素材时间：未知");
    assertThat(block).contains("不得满分");
  }

  @Test
  void summarize_falls_back_to_topic_publish_window() {
    String summary = TopicWritingScoreTimeFacts.summarizeMaterialTimes(
        List.of(HotNewsTopicSourceView.builder().url("https://a.example/1").build()),
        LocalDateTime.of(2026, 7, 18, 10, 0),
        LocalDateTime.of(2026, 7, 20, 12, 0));
    assertThat(summary).contains("最早 2026-07-18 10:00");
    assertThat(summary).contains("最晚 2026-07-20 12:00");
    assertThat(summary).contains("回退自选题聚合");
  }

  @Test
  void summarize_merges_topic_window_when_earlier_than_ready_sources() {
    String summary = TopicWritingScoreTimeFacts.summarizeMaterialTimes(
        List.of(
            HotNewsTopicSourceView.builder()
                .url("https://ready.example/1")
                .publishedAt(LocalDateTime.of(2026, 7, 20, 12, 0))
                .build()),
        LocalDateTime.of(2026, 7, 18, 10, 0),
        LocalDateTime.of(2026, 7, 20, 12, 0));
    assertThat(summary).contains("最早 2026-07-18 10:00");
    assertThat(summary).contains("最晚 2026-07-20 12:00");
    assertThat(summary).contains("已与选题聚合窗口合并");
  }

  @Test
  void summarize_keeps_source_window_when_topic_window_not_wider() {
    String summary = TopicWritingScoreTimeFacts.summarizeMaterialTimes(
        List.of(
            HotNewsTopicSourceView.builder()
                .url("https://a.example/1")
                .publishedAt(LocalDateTime.of(2026, 7, 18, 10, 0))
                .build(),
            HotNewsTopicSourceView.builder()
                .url("https://b.example/2")
                .publishedAt(LocalDateTime.of(2026, 7, 20, 12, 0))
                .build()),
        LocalDateTime.of(2026, 7, 18, 10, 0),
        LocalDateTime.of(2026, 7, 20, 12, 0));
    assertThat(summary).contains("最早 2026-07-18 10:00");
    assertThat(summary).contains("最晚 2026-07-20 12:00");
    assertThat(summary).contains("来源有发布时间 2 / 共 2");
    assertThat(summary).doesNotContain("已与选题聚合窗口合并");
    assertThat(summary).doesNotContain("回退自选题聚合");
  }

  @Test
  void formatSourceLines_keeps_rows_without_url() {
    String lines = TopicWritingScoreTimeFacts.formatSourceLines(List.of(
        HotNewsTopicSourceView.builder()
            .siteLabel("新华财经")
            .url("https://cnfin.com/a")
            .title("WAIC")
            .publishedAt(LocalDateTime.of(2026, 7, 18, 9, 30))
            .build(),
        HotNewsTopicSourceView.builder()
            .siteLabel("无链来源")
            .title("仅有时间")
            .publishedAt(LocalDateTime.of(2026, 7, 19, 8, 0))
            .build()), 12);
    assertThat(lines).contains("published=2026-07-18 09:30");
    assertThat(lines).contains("(无URL)");
    assertThat(lines).contains("published=2026-07-19 08:00");
  }
}
