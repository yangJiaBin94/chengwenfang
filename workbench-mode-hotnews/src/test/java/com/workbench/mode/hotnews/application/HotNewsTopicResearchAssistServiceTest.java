package com.workbench.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.mode.hotnews.application.research.ResearchKeywordPlan;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HotNewsTopicResearchAssistServiceTest {
  @Test
  void parse_keyword_plan_from_json() {
    ResearchKeywordPlan plan = HotNewsTopicResearchAssistService.parseKeywordPlan("""
        {"subject":"Apple Inc","keywords":["Apple Inc","Apple 公司"],"disambiguation":"苹果公司，不是水果"}
        """);
    assertThat(plan).isNotNull();
    assertThat(plan.getSubject()).isEqualTo("Apple Inc");
    assertThat(plan.getKeywords()).contains("Apple Inc", "Apple 公司");
    assertThat(plan.getDisambiguation()).contains("不是水果");
  }

  @Test
  void parse_relevance_filters_false_items() {
    Map<String, Boolean> judgments = HotNewsTopicResearchAssistService.parseRelevance("""
        {"items":[
          {"id":"0","relevant":true,"reason":"苹果公司财报"},
          {"id":"1","relevant":false,"reason":"讲的是水果苹果"}
        ]}
        """);
    assertThat(judgments.get("0")).isTrue();
    assertThat(judgments.get("1")).isFalse();
  }

  @Test
  void parse_translations() {
    var translated = HotNewsTopicResearchAssistService.parseTranslations("""
        {"items":[{"id":"0","title":"英伟达押注 Reflection","summary":"摘要"}]}
        """);
    assertThat(translated.get("0").title()).isEqualTo("英伟达押注 Reflection");
    assertThat(translated.get("0").summary()).isEqualTo("摘要");
  }

  @Test
  void parse_skeleton() {
    var skeleton = HotNewsTopicResearchAssistService.parseSkeleton("t1", """
        {"workingTitle":"Beam 发布","scope":"只谈开源模型","coreThesis":"后训练进入新阶段",
         "illustrationStyle":"对照示意图","coverBrief":"Beam 发布冲突感封面",
         "recommendedWordCountOption":"2500-4000","recommendedImageCountOption":"0",
         "architecture":["事实","影响"],"flow":["开场","收束"],
         "modules":[{"name":"事实","purpose":"交代","keyPoints":["发布"]}],
         "referenceHints":["CSDN 简报"]}
        """);
    assertThat(skeleton).isNotNull();
    assertThat(skeleton.getWorkingTitle()).isEqualTo("Beam 发布");
    assertThat(skeleton.getCoreThesis()).contains("后训练");
    assertThat(skeleton.getModules()).hasSize(1);
    assertThat(skeleton.getIllustrationStyle()).isEqualTo("对照示意图");
    assertThat(skeleton.getRecommendedWordCountOption()).isEqualTo("2500-4000");
    // 旧档 0 规范化为至少 2 张正文插图
    assertThat(skeleton.getRecommendedImageCountOption()).isEqualTo("2-3");
    // 缺省 imagePromptRequirements 时回填默认生图要求
    assertThat(skeleton.getImagePromptRequirements()).isNotBlank();
  }
}
