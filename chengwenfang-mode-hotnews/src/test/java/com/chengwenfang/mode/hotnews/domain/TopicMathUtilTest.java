package com.chengwenfang.mode.hotnews.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;

class TopicMathUtilTest {
  @Test
  void similar_titles_match_near_duplicates() {
    assertThat(TopicMathUtil.similar("OpenAI 发布新的 AI 模型", "OpenAI 发布新 AI 模型")).isTrue();
    assertThat(TopicMathUtil.similar("完全无关标题 A", "另一个主题 B")).isFalse();
  }

  @Test
  void aiThemeSeedNeed_padsDeficitAndKeepsAtLeastOneWhenEmptySeeds() {
    assertThat(TopicMathUtil.aiThemeSeedNeed(0, 0)).isEqualTo(10);
    assertThat(TopicMathUtil.aiThemeSeedNeed(3, 0)).isEqualTo(7);
    assertThat(TopicMathUtil.aiThemeSeedNeed(9, 0)).isEqualTo(1);
    assertThat(TopicMathUtil.aiThemeSeedNeed(15, 0)).isEqualTo(1);
    assertThat(TopicMathUtil.aiThemeSeedNeed(10, 7)).isZero();
    assertThat(TopicMathUtil.aiThemeSeedNeed(12, 1)).isZero();
  }

  @Test
  void daily_limit_keeps_top_ten_by_score() {
    List<Row> rows = IntStream.range(0, 25)
        .mapToObj(index -> new Row("2026-09-07", index, "t-" + index))
        .toList();
    List<Row> kept = TopicMathUtil.keepTopPerDay(rows, Row::day, Row::score);
    assertThat(kept).hasSize(10);
    assertThat(kept.get(0).score()).isEqualTo(24);
    assertThat(kept.get(9).score()).isEqualTo(15);
  }

  @Test
  void score_cluster_rewards_independent_recent_sources_but_defers_writing() {
    LocalDateTime now = LocalDateTime.parse("2026-09-07T12:00:00");
    List<TopicMathUtil.SourceEvidence> sources = List.of(
        new TopicMathUtil.SourceEvidence("https://openai.com/a", "c1", now.minusSeconds(3600)),
        new TopicMathUtil.SourceEvidence("https://techcrunch.com/b", "c2", now.minusSeconds(7200)));
    TopicMathUtil.ClusterScore score = TopicMathUtil.scoreCluster(
        "OpenAI 发布新 AI 模型", "大模型开源与推理能力更新", sources, now);
    assertThat(score.heatScore()).isGreaterThan(40);
    // 写作价值改由主题量表 AI 评分，规则阶段固定为 0
    assertThat(score.writingScore()).isZero();
    assertThat(score.confidenceScore()).isEqualTo(score.evidenceScore());
    assertThat(score.lifecycle()).isIn("early", "rising", "peak", "cooling", "evergreen");
    assertThat(score.scoreReason()).contains("独立来源").contains("主题量表");
    assertThat(score.scoreDetails().getMetricNote()).contains("主题量表");
  }

  @Test
  void single_source_applies_weak_penalty_and_stays_early() {
    LocalDateTime now = LocalDateTime.parse("2026-09-07T12:00:00");
    TopicMathUtil.ClusterScore score = TopicMathUtil.scoreCluster(
        "普通标题",
        "普通摘要",
        List.of(new TopicMathUtil.SourceEvidence("https://forum.example/x", "c1", now.minusSeconds(1800))),
        now);
    assertThat(score.lifecycle()).isEqualTo("early");
    assertThat(score.evidenceScore()).isLessThan(40);
  }

  @Test
  void unknown_publish_time_does_not_get_max_freshness() {
    LocalDateTime now = LocalDateTime.parse("2026-10-08T12:00:00");
    TopicMathUtil.ClusterScore score = TopicMathUtil.scoreCluster(
        "GPT-5两周内发布",
        "旧闻",
        List.of(new TopicMathUtil.SourceEvidence("https://www.36kr.com/p/1", "c1", null)),
        now);
    assertThat(score.freshnessScore()).isLessThanOrEqualTo(35);
  }

  /**
   * 按日截断用例的行夹具：日期、分数与主题 id。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  private static final class Row {
    /** 业务日（yyyy-MM-dd）。 */
    private final String day;
    /** 排序用分数。 */
    private final int score;
    /** 主题 id。 */
    private final String id;
  }
}
