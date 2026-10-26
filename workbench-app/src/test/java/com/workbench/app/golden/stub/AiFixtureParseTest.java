package com.workbench.app.golden.stub;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.workbench.app.application.generation.custom.CustomAiActionPlanParser;
import com.workbench.mode.hotnews.application.generation.radarcollect.RadarCollectAiSupport;
import com.workbench.mode.hotnews.application.generation.radarcollect.model.ArticlePublishDateAiResult;
import com.workbench.mode.hotnews.application.generation.radarcollect.model.MarketingFilterAiResult;
import com.workbench.mode.hotnews.application.hotnews.GoldenAppAiParsers;
import com.workbench.common.LlmJsonSupport;
import com.workbench.content.facade.ScoringRubricSupport;
import com.workbench.content.facade.dto.view.ScoringRubricView;
import com.workbench.mode.hotnews.application.GoldenHotNewsAiParsers;
import com.workbench.mode.hotnews.application.research.ResearchKeywordPlan;
import com.workbench.mode.hotnews.facade.constant.ResearchItemScoringRubric;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 验证每个 AI fixture 的助手内容可被对应业务解析器（或等价结构校验）接受。
 */
class AiFixtureParseTest {
  private static final CustomAiActionPlanParser ACTION_PARSER = new CustomAiActionPlanParser();

  static Stream<AiFixtureRule> rules() {
    return AiFixtureCatalog.loadFromClasspath().rules().stream();
  }

  /**
   * @param rule fixture 规则
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("rules")
  void fixture_content_is_parsable(AiFixtureRule rule) {
    assertThat(rule.scene()).isNotBlank();
    String parser = rule.parser() == null ? "text" : rule.parser();
    switch (parser) {
      case "text" -> {
        if (rule.forceEmpty()) {
          assertThat(rule.assistantText()).isEmpty();
        } else {
          assertThat(rule.assistantText().trim()).isNotBlank();
        }
      }
      case "image" -> assertThat(rule.b64Json()).isNotBlank();
      case "keyword_plan" -> {
        ResearchKeywordPlan plan = GoldenHotNewsAiParsers.parseKeywordPlan(rule.assistantText());
        assertThat(plan).isNotNull();
        assertThat(plan.getKeywords()).isNotEmpty();
      }
      case "relevance" -> {
        Map<String, Boolean> map = GoldenHotNewsAiParsers.parseRelevance(rule.assistantText());
        assertThat(map).isNotEmpty();
      }
      case "research_translate" -> {
        var translated = GoldenHotNewsAiParsers.parseTranslations(rule.assistantText());
        assertThat(translated).isNotEmpty();
      }
      case "skeleton" -> {
        var skeleton = GoldenHotNewsAiParsers.parseSkeleton("t1", rule.assistantText());
        assertThat(skeleton).isNotNull();
        assertThat(skeleton.getCoreThesis()).isNotBlank();
      }
      case "theme_seed" -> assertThat(RadarCollectAiSupport.parseAiThemeSeedItems(rule.assistantText()))
          .isNotEmpty();
      case "marketing" -> {
        MarketingFilterAiResult result =
            LlmJsonSupport.parseObject(rule.assistantText(), MarketingFilterAiResult.class);
        assertThat(result).isNotNull();
        assertThat(result.getMarketing()).isNotNull();
      }
      case "publish_date" -> {
        ArticlePublishDateAiResult result =
            LlmJsonSupport.parseObject(rule.assistantText(), ArticlePublishDateAiResult.class);
        assertThat(result).isNotNull();
      }
      case "channels" -> assertThat(GoldenAppAiParsers.parseChannels(rule.assistantText())).isNotEmpty();
      case "queries" -> {
        JSONObject json = JSONUtil.parseObj(rule.assistantText());
        assertThat(json.getJSONArray("queries")).isNotEmpty();
      }
      case "github_query" -> {
        JSONObject json = JSONUtil.parseObj(rule.assistantText());
        assertThat(json.getStr("query")).isNotBlank();
      }
      case "scoring_rubric" -> {
        ScoringRubricView rubric =
            LlmJsonSupport.parseObject(rule.assistantText(), ScoringRubricView.class);
        assertThat(ScoringRubricSupport.isValid(rubric)).isTrue();
      }
      case "research_item_score" -> assertResearchItemScore(rule.assistantText());
      case "topic_translation" -> {
        JSONObject json = JSONUtil.parseObj(rule.assistantText());
        JSONArray translations = json.getJSONArray("translations");
        assertThat(translations).isNotEmpty();
        assertThat(translations.getJSONObject(0).getStr("title")).matches(".*[\\u4e00-\\u9fff].*");
      }
      case "writing_score" -> {
        JSONObject json = JSONUtil.parseObj(rule.assistantText());
        assertThat(json.getJSONArray("dimensions")).isNotEmpty();
        assertThat(json.getStr("editorReason")).isNotBlank();
      }
      case "promo_judge" -> {
        JSONObject json = JSONUtil.parseObj(rule.assistantText());
        assertThat(json.getStr("intent")).isIn("knowledge", "marketing");
      }
      case "adhoc_actions" -> assertThat(ACTION_PARSER.parse(rule.assistantText())).isNotEmpty();
      case "curriculum" -> {
        JSONObject json = JSONUtil.parseObj(rule.assistantText());
        assertThat(json.getStr("routeNarrative")).isNotBlank();
        assertThat(json.getJSONArray("series")).isNotEmpty();
      }
      case "json_object" -> assertThat(JSONUtil.isTypeJSONObject(rule.assistantText())).isTrue();
      default -> throw new IllegalStateException("未知 parser=" + parser + " scene=" + rule.scene());
    }
  }

  private static void assertResearchItemScore(String text) {
    JSONObject json = JSONUtil.parseObj(text);
    JSONArray items = json.getJSONArray("items");
    assertThat(items).isNotEmpty();
    Set<String> expected =
        ResearchItemScoringRubric.dimensions().stream()
            .map(dim -> dim.getKey())
            .collect(Collectors.toSet());
    JSONArray dimensions = items.getJSONObject(0).getJSONArray("dimensions");
    Set<String> actual =
        dimensions.stream()
            .map(row -> ((JSONObject) row).getStr("key"))
            .collect(Collectors.toSet());
    assertThat(actual).isEqualTo(expected);
  }
}
