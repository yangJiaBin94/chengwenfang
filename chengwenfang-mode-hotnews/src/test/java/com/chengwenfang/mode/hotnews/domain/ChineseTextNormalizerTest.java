package com.chengwenfang.mode.hotnews.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChineseTextNormalizerTest {
  @Test
  void converts_traditional_title_and_summary_to_simplified() {
    assertThat(ChineseTextNormalizer.toSimplified(
            "LLM 評測指標完整解讀：15 個常見Benchmark"))
        .isEqualTo("LLM 评测指标完整解读：15 个常见Benchmark");
    assertThat(ChineseTextNormalizer.toSimplified(
            "中美AI競賽｜月之暗面被控「出貓」 用Claude答覆回應Kimi用戶"))
        .contains("竞赛")
        .contains("出猫")
        .contains("答复")
        .contains("回应")
        .doesNotContain("競賽")
        .doesNotContain("答覆");
  }

  @Test
  void leaves_null_blank_and_simplified_unchanged() {
    assertThat(ChineseTextNormalizer.toSimplified(null)).isNull();
    assertThat(ChineseTextNormalizer.toSimplified("")).isEmpty();
    assertThat(ChineseTextNormalizer.toSimplified("开源大模型食用指南")).isEqualTo("开源大模型食用指南");
  }
}
