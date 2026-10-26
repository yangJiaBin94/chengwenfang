package com.chengwenfang.mode.hotnews.facade.constant;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.domain.TopicDimensionScore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 资料池固定五维加权公式。 */
class ResearchItemScoringRubricTest {

  @Test
  void dimensions_权重之和为100() {
    int sum = ResearchItemScoringRubric.dimensions().stream()
        .mapToInt(dim -> dim.getWeight() == null ? 0 : dim.getWeight())
        .sum();
    assertThat(sum).isEqualTo(100);
    assertThat(ResearchItemScoringRubric.dimensions()).hasSize(5);
  }

  @Test
  void computeOverall_加权合成() {
    // 10×0.3 + 8×0.3 + 6×0.15 + 8×0.15 + 7×0.10 = 3+2.4+0.9+1.2+0.7 = 8.2 → 82
    Map<String, Integer> scores = new LinkedHashMap<>();
    scores.put("relevance", 10);
    scores.put("credibility", 8);
    scores.put("freshness", 6);
    scores.put("independence", 8);
    scores.put("writability", 7);
    assertThat(ResearchItemScoringRubric.computeOverall(scores)).isEqualTo(82);
  }

  @Test
  void computeOverall_缺维按0() {
    Map<String, Integer> scores = Map.of("relevance", 10);
    // 10×0.3 = 3.0 → 30
    assertThat(ResearchItemScoringRubric.computeOverall(scores)).isEqualTo(30);
  }

  @Test
  void fillDimensions_补齐量表并对分数夹紧() {
    Map<String, Integer> scores = Map.of("relevance", 12, "credibility", -1);
    Map<String, String> reasons = Map.of("relevance", "高度相关");
    List<TopicDimensionScore> dims =
        ResearchItemScoringRubric.fillDimensions(scores, reasons);
    assertThat(dims).hasSize(5);
    assertThat(dims.get(0).getScore()).isEqualTo(10);
    assertThat(dims.get(0).getReason()).isEqualTo("高度相关");
    assertThat(dims.get(1).getScore()).isEqualTo(0);
    assertThat(dims.get(2).getScore()).isEqualTo(0);
  }
}
