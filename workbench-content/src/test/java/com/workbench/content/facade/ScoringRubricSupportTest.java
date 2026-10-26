package com.workbench.content.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workbench.common.BusinessException;
import com.workbench.content.facade.dto.view.ScoringRubricDimensionView;
import com.workbench.content.facade.dto.view.ScoringRubricView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link ScoringRubricSupport} 单测。 */
class ScoringRubricSupportTest {

  @Test
  void assertValid_acceptsThreeDimsSum100() {
    ScoringRubricView rubric = sampleRubric();
    ScoringRubricSupport.assertValid(rubric);
    assertThat(ScoringRubricSupport.isValid(rubric)).isTrue();
  }

  @Test
  void assertValid_rejectsWeightSumNot100() {
    ScoringRubricView rubric = sampleRubric();
    rubric.getDimensions().get(0).setWeight(50);
    assertThatThrownBy(() -> ScoringRubricSupport.assertValid(rubric))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("权重之和");
  }

  @Test
  void computeWritingScore_weightedAverage() {
    ScoringRubricView rubric = sampleRubric();
    Map<String, Integer> scores = new LinkedHashMap<>();
    scores.put("theme_fit", 10);
    scores.put("audience_payoff", 8);
    scores.put("angle_potential", 6);
    // 10*0.4 + 8*0.35 + 6*0.25 = 4+2.8+1.5 = 8.3 → *10 = 83
    assertThat(ScoringRubricSupport.computeWritingScore(rubric, scores)).isEqualTo(83);
  }

  @Test
  void readWrite_roundTrip() {
    Map<String, Object> briefing = new LinkedHashMap<>();
    ScoringRubricSupport.writeInto(briefing, sampleRubric());
    ScoringRubricView loaded = ScoringRubricSupport.readFrom(briefing);
    assertThat(loaded).isNotNull();
    assertThat(loaded.getDimensions()).hasSize(3);
    assertThat(loaded.getDimensions().get(0).getKey()).isEqualTo("theme_fit");
  }

  private static ScoringRubricView sampleRubric() {
    return ScoringRubricView.builder()
        .version(1)
        .generatedAt("2026-10-14T00:00:00")
        .scoreGuidance("触碰 out_of_scope 整体压低")
        .dimensions(List.of(
            dim("theme_fit", "主题契合", "是否落在主题范围内", 40),
            dim("audience_payoff", "读者收益", "对目标读者是否有可带走启发", 35),
            dim("angle_potential", "成文切入", "能否写出差异化角度", 25)))
        .build();
  }

  private static ScoringRubricDimensionView dim(
      String key, String label, String description, int weight) {
    return ScoringRubricDimensionView.builder()
        .key(key)
        .label(label)
        .description(description)
        .weight(weight)
        .build();
  }
}
