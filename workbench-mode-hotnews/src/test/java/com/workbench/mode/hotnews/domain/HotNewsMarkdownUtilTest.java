package com.workbench.mode.hotnews.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link HotNewsMarkdownUtil} 插图提示词与字数规则。 */
class HotNewsMarkdownUtilTest {

  @Test
  void countBodyCharsExcludesImagePromptBlocks() {
    String markdown = """
        这是正文第一段，讲核心变化。

        **插图位置：封面图**

        ```text
        热点公众号封面。复杂提示词很长很长很长很长很长。
        ```

        这是正文第二段。

        **插图位置：插图 1｜核心变化**

        ```text
        正文插图提示词也不计入字数。
        ```

        ```python
        print("代码也不计入")
        ```
        """;
    int count = HotNewsMarkdownUtil.countBodyChars(markdown);
    assertThat(count).isLessThan(40);
    assertThat(count).isGreaterThan(10);
    assertThat(count).isEqualTo(HotNewsMarkdownUtil.countBodyChars(
        "这是正文第一段，讲核心变化。\n\n这是正文第二段。"));
  }

  @Test
  void prepareWechatSourceReplacesInlinePromptsAndDropsCover() {
    String draft = """
        正文开头。

        **插图位置：封面图**

        ```text
        封面提示词
        ```

        中间段落。

        **插图位置：插图 1｜对比**

        ```text
        正文图一
        ```
        """;
    HotNewsMarkdownUtil.WechatSource source = HotNewsMarkdownUtil.prepareWechatSource(draft);
    assertThat(source.prepared()).doesNotContain("封面提示词");
    assertThat(source.prepared()).doesNotContain("正文图一");
    assertThat(source.prepared()).contains("[[HOT_NEWS_IMAGE_1]]");
    assertThat(source.imageTokens()).containsExactly("[[HOT_NEWS_IMAGE_1]]");
  }

  @Test
  void ensureImagePromptsAddsCoverAndInlineBlocks() {
    String ensured = HotNewsMarkdownUtil.ensureImagePrompts(
        "只有一段正文。",
        2,
        "测试标题",
        "科技扁平",
        "冲突感封面",
        HotNewsMarkdownUtil.defaultImagePromptRequirements());
    assertThat(HotNewsMarkdownUtil.coverPrompt(ensured)).isNotBlank();
    assertThat(HotNewsMarkdownUtil.findInlinePrompts(ensured)).hasSize(2);
  }

  @Test
  void attachImageAfterPromptKeepsPromptAndPlacesImageBelow() {
    String draft = """
        正文。

        **插图位置：插图 1｜对比**

        ```text
        正文图一
        ```
        """;
    String updated = HotNewsMarkdownUtil.attachImageAfterPrompt(
        draft, "正文图一", "![](/api/images/a.png)");
    assertThat(updated).contains("正文图一");
    assertThat(updated.indexOf("```")).isLessThan(updated.indexOf("![](/api/images/a.png)"));
    assertThat(HotNewsMarkdownUtil.draftImagesFollowPrompts(updated)).isTrue();
  }

  @Test
  void hasPromptLeakDetectsIllustrationCaptionTitles() {
    assertThat(HotNewsMarkdownUtil.hasPromptLeak("正文\n\n插图 2\n\n更多")).isTrue();
    assertThat(HotNewsMarkdownUtil.hasPromptLeak(
        "正文\n\n![](/api/images/a.png)\n\n更多")).isFalse();
  }

  @Test
  void canonicalizeConvertsBarePromptLinesToStandardBlocks() {
    String dirty = """
        # 标题

        正文一段。

        插图位置：插图 1｜核心冲突

        提示词：深海蓝三维科技空间，Apple Keynote 风格，高端 SaaS 仪表盘美学，16:9比例 --v 6
        """;
    String cleaned = HotNewsMarkdownUtil.canonicalizeIllustrationPrompts(dirty);
    assertThat(cleaned).contains("**插图位置：插图 1｜核心冲突**");
    assertThat(cleaned).contains("```text");
    assertThat(HotNewsMarkdownUtil.findPrompts(cleaned)).isNotEmpty();
  }

  @Test
  void hasPromptLeakDetectsBareMidjourneyParagraph() {
    String wechat = """
        正文说明。

        深海蓝三维科技空间，Apple Keynote 风格，高端 SaaS 仪表盘美学，悬浮玻璃结构，16:9比例 --v 6

        更多正文。
        """;
    assertThat(HotNewsMarkdownUtil.hasPromptLeak(wechat)).isTrue();
  }

  @Test
  void stripWechatProductionResidueRemovesBareMidjourneyParagraph() {
    String wechat = """
        这些阶段最后都要回到原文片段。

        深海蓝三维科技空间，Apple Keynote 风格，高端 SaaS 仪表盘美学，悬浮玻璃结构有厚度和透视，16:9比例 --v 6

        正确回答需要同时满足三个条件。
        """;
    String cleaned = HotNewsMarkdownUtil.stripWechatProductionResidue(wechat);
    assertThat(cleaned).contains("这些阶段最后都要回到原文片段");
    assertThat(cleaned).contains("正确回答需要同时满足三个条件");
    assertThat(cleaned).doesNotContain("深海蓝三维科技空间");
    assertThat(cleaned).doesNotContain("--v 6");
  }

  @Test
  void sanitizeProseSymbolsClearsForbiddenMarksOutsideFences() {
    String cleaned = HotNewsMarkdownUtil.sanitizeProseSymbols(
        "这是「关键」概念 // 注释 -> 结果 —— 结束。");
    assertThat(cleaned).contains("“关键”").contains("｜").contains("→");
    assertThat(cleaned).doesNotContain("「").doesNotContain("——").doesNotContain("->");
  }
}
