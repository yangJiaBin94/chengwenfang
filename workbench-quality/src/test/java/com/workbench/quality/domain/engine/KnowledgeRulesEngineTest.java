package com.workbench.quality.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class KnowledgeRulesEngineTest {
  private final KnowledgeRulesEngine engine = new KnowledgeRulesEngine();

  @Test
  void reject_publishing_format_violations() {
    String article = "# 第1课：AI\n\n| a | b |\n|---|---|\n\n```text\ncode\n```\n\n---\n";
    Map<String, KnowledgeRulesEngine.KnowledgeRule> result = byCode(article, "");
    assertThat(result.get("TITLE_NO_COURSE_NUMBER").passed()).isFalse();
    assertThat(result.get("NO_MARKDOWN_TABLE").passed()).isFalse();
    assertThat(result.get("NO_CODE_FENCE").passed()).isFalse();
    assertThat(result.get("NO_HORIZONTAL_RULE").passed()).isFalse();
  }

  @Test
  void accept_clean_article_and_check_preservation() {
    String source = "# 标题\n\n他说：“先从直觉开始。”\n\n" + "正文内容。".repeat(20);
    Map<String, KnowledgeRulesEngine.KnowledgeRule> result = byCode(source, source);
    assertThat(result.values()).allMatch(KnowledgeRulesEngine.KnowledgeRule::passed);
  }

  @Test
  void english_quotes_in_wechat_python_code_are_not_treated_as_prose() {
    String article = """
        # Python 字符串

        > course_name = "Python 基础"
        > message = (
        > &nbsp;&nbsp;&nbsp;&nbsp;course_name
        > &nbsp;&nbsp;&nbsp;&nbsp;+ "：共学习 "
        > &nbsp;&nbsp;&nbsp;&nbsp;+ str(40)
        > &nbsp;&nbsp;&nbsp;&nbsp;+ " 分钟"
        > )

        正文中的字符串示例写作 `"学习时长：" + str(40)`。
        """;
    assertThat(byCode(article, "").get("NO_ENGLISH_QUOTES").passed()).isTrue();
  }

  @Test
  void english_quotes_in_chinese_prose_still_fail_quote_rule() {
    String article = "# 标题\n\n老师说：\"先运行代码，再核对输出。\"\n";
    assertThat(byCode(article, "").get("NO_ENGLISH_QUOTES").passed()).isFalse();
  }

  @Test
  void markdown_and_html_attribute_quotes_are_not_treated_as_prose() {
    String article = """
        # 标题

        [Python 文档](https://www.python.org/ "官方文档")

        <img src="lesson.png" alt="课程插图">
        """;
    assertThat(byCode(article, "").get("NO_ENGLISH_QUOTES").passed()).isTrue();
  }

  @Test
  void preservation_ignores_image_generation_instructions() {
    String body = "核心知识、公式、案例和推理都完整保留。".repeat(20);
    String prompt = "一段很长的生图提示词，不属于公众号正文。".repeat(40);
    String source = "# 课程原稿\n\n" + body + "\n\n"
        + "> ![插图](../images/example.png)\n>\n"
        + "> **插图位置**：展示核心流程\n>\n"
        + "> **提示词**：\n> ```\n> " + prompt + "\n> ```\n";
    String article = "# 公众号标题\n\n" + body + "\n\n![插图](../images/example.png)";
    assertThat(byCode(article, source).get("CONTENT_PRESERVATION").passed()).isTrue();
  }

  @Test
  void allow_neural_network_depth_as_technical_term() {
    String article = "# 从单层到多层\n\n网络深度表示信息连续经历多少层转换，宽度表示每层的神经元数量。";
    assertThat(byCode(article, "").get("NO_AI_CLICHES").passed()).isTrue();
  }

  @Test
  void report_specific_promotional_cliche() {
    String article = "# 标题\n\n这套方案将深度赋能内容生产。";
    KnowledgeRulesEngine.KnowledgeRule rule = byCode(article, "").get("NO_AI_CLICHES");
    assertThat(rule.passed()).isFalse();
    assertThat(rule.detail()).contains("深度套话");
  }

  @Test
  void formula_blocks_are_normalized_to_markdown_quotes() {
    String article = "公式如下：\n\n[ \\operatorname{Tanh}(z)=\\frac{e^z-e^{-z}}{e^z+e^{-z}} ]\n";
    String normalized = engine.normalizeFormulaBlocks(article);
    assertThat(normalized).contains("> Tanh(z) = (e^z - e^(-z)) / (e^z + e^(-z))");
    assertThat(byCode(article, "").get("FORMULA_BLOCK_STYLE").passed()).isFalse();
  }

  @Test
  void styled_formula_block_passes_formula_rule() {
    String article = "公式如下：\n\n> z = w · x\n";
    assertThat(byCode(article, "").get("FORMULA_BLOCK_STYLE").passed()).isTrue();
  }

  @Test
  void latex_commands_in_styled_formula_fail_plain_expression_rule() {
    String article = "> \\operatorname{Tanh}(z)=\\frac{e^z-e^{-z}}{e^z+e^{-z}}\n";
    Map<String, KnowledgeRulesEngine.KnowledgeRule> result = byCode(article, "");
    assertThat(result.get("FORMULA_BLOCK_STYLE").passed()).isTrue();
    assertThat(result.get("FORMULA_PLAIN_EXPRESSION").passed()).isFalse();
  }

  @Test
  void plain_formula_expression_passes_both_formula_rules() {
    String article = "> Tanh(z) = (e^z - e^(-z)) / (e^z + e^(-z))\n";
    Map<String, KnowledgeRulesEngine.KnowledgeRule> result = byCode(article, "");
    assertThat(result.get("FORMULA_BLOCK_STYLE").passed()).isTrue();
    assertThat(result.get("FORMULA_PLAIN_EXPRESSION").passed()).isTrue();
  }

  @Test
  void workflow_image_placeholders_are_not_treated_as_formulas() {
    String article = "[[COURSE_COVER]]\n\n[[COURSE_IMAGE_1]]\n";
    assertThat(engine.normalizeFormulaBlocks(article)).isEqualTo(article);
  }

  private Map<String, KnowledgeRulesEngine.KnowledgeRule> byCode(String article, String source) {
    return engine.check(article, source).stream()
        .collect(Collectors.toMap(KnowledgeRulesEngine.KnowledgeRule::code, Function.identity()));
  }
}
