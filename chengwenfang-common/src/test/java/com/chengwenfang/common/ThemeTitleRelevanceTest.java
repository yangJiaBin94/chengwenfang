package com.chengwenfang.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ThemeTitleRelevance} 单测。 */
class ThemeTitleRelevanceTest {
  private static final ThemeTitleRelevance.Profile ELDERLY = new ThemeTitleRelevance.Profile(
      "中老年身心健康",
      List.of(
          "中老年科学养生方法",
          "中老年认知衰退预防",
          "中老年心理健康维护",
          "中老年心脑血管养护"),
      List.of());

  @Test
  void keepsOnTopicTitles() {
    assertTrue(ThemeTitleRelevance.judge(ELDERLY, "经常转头忘事｜分清正常健忘和认知衰退，护脑全方案")
        .relevant());
    assertTrue(ThemeTitleRelevance.judge(ELDERLY, "肩颈保护操，动作温和，适合中老年日常养护！")
        .relevant());
    assertTrue(ThemeTitleRelevance.judge(ELDERLY, "老年人怎样维护心理健康")
        .relevant());
  }

  @Test
  void dropsObviousOffTopicTitles() {
    assertFalse(ThemeTitleRelevance.judge(
            ELDERLY, "成为恋爱赢家。必知十四条信息，清醒去爱。 #提升认知。慢慢变好、智者必修")
        .relevant());
    assertFalse(ThemeTitleRelevance.judge(
            ELDERLY, "🔥2026诺贝尔经济学奖｜为什么经济总是通胀和衰退反复横跳？【播客】")
        .relevant());
    assertFalse(ThemeTitleRelevance.judge(ELDERLY, "贷后全链条训练过程据篇-催收体检表")
        .relevant());
  }

  @Test
  void blankProfileFailsOpen() {
    assertTrue(ThemeTitleRelevance.judge(new ThemeTitleRelevance.Profile("", List.of(), List.of()), "任意标题")
        .relevant());
  }

  @Test
  void keepsAiTitlesThatOnlyShareASynonym() {
    ThemeTitleRelevance.Profile frontier = new ThemeTitleRelevance.Profile(
        "AI 前沿热点",
        List.of("大模型 发布", "AI Agent", "开源 LLM"),
        List.of("大模型", "Agent", "开源发布"));
    assertTrue(ThemeTitleRelevance.judge(
        frontier,
        "Meta Open-Sources Multimodal Model Muse Spark Open; NVIDIA Launches Nemotron")
        .relevant());
    assertTrue(ThemeTitleRelevance.judge(frontier, "发现 MUSE SPARK 开源版、GPT-6.1 泄露").relevant());
    assertTrue(ThemeTitleRelevance.judge(frontier, "不到一个月多个模型开源，小参数模型目前还在进步！").relevant());
    assertTrue(ThemeTitleRelevance.judge(frontier, "Qwen本地新模型！10月20日 AI日报").relevant());
    assertTrue(ThemeTitleRelevance.judge(frontier, "用AI两周验证一个爆款IP").relevant());
  }

  @Test
  void leavesNewModelNamesForTheThemeModel() {
    ThemeTitleRelevance.Profile frontier = new ThemeTitleRelevance.Profile(
        "AI 前沿热点",
        List.of("大模型 发布", "AI Agent", "开源 LLM"),
        List.of("大模型", "Agent", "开源发布"));
    ThemeTitleRelevance.Decision decision = ThemeTitleRelevance.judge(
        frontier, "Fable 5.5 提前出现，Opus 6 泄露，Meta 发布 Muse Spark Open");
    assertFalse(decision.relevant());
    assertTrue(decision.uncertain());
    assertFalse(decision.hardDrop());
  }
}
