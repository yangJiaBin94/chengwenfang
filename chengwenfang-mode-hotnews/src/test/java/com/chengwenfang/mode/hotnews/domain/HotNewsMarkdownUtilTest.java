package com.chengwenfang.mode.hotnews.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
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
  void findInlinePromptsIgnoresCoverKeywordInsideInlinePromptBody() {
    // 回归：骨架 imagePromptRequirements 含「封面要…」时，不得把「插图 N」块算成封面
    String draft = """
        正文开头。

        **插图位置：插图 1｜口径对照**

        ```text
        正文图一。
        ```

        **插图位置：插图 2｜影响面对比**

        ```text
        画面讲影响面。封面要一眼读出「同一只新股、三种涨幅口径」的核心冲突。
        ```
        """;
    assertThat(HotNewsMarkdownUtil.findInlinePrompts(draft)).hasSize(2);
    assertThat(HotNewsMarkdownUtil.coverPrompt(draft)).isBlank();
  }

  @Test
  void ensureImagePromptsCountsInlineWhenRequirementsMentionCover() {
    String ensured = HotNewsMarkdownUtil.ensureImagePrompts(
        "只有一段正文。",
        2,
        "测试标题",
        "科技扁平",
        "冲突感",
        "封面要一眼读出核心冲突；用 3～5 个信息元素讲清对比。");
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
  void attachImageAfterPromptDoesNotDuplicateExistingImage() {
    String draft = """
        **插图位置：插图 1｜对比**

        ```text
        正文图一
        ```

        ![](/api/images/a.png)
        """;
    String updated = HotNewsMarkdownUtil.attachImageAfterPrompt(
        draft, "正文图一", "![](/api/images/a.png)");
    assertThat(updated.split(Pattern.quote("![](/api/images/a.png)"), -1)).hasSize(2);
    assertThat(HotNewsMarkdownUtil.draftImagesFollowPrompts(updated)).isTrue();
  }

  @Test
  void attachImageAfterPromptReplacesPriorDuplicateStack() {
    String draft = """
        **插图位置：插图 1｜对比**

        ```text
        正文图一
        ```

        ![](/api/images/old.png)
        ![](/api/images/old.png)
        """;
    String updated = HotNewsMarkdownUtil.attachImageAfterPrompt(
        draft, "正文图一", "![](/api/images/new.png)");
    assertThat(updated).contains("![](/api/images/new.png)");
    assertThat(updated).doesNotContain("![](/api/images/old.png)");
    assertThat(updated.split(Pattern.quote("![](/api/images/new.png)"), -1)).hasSize(2);
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

  @Test
  void extractWechatDigestLineReadsQuotedSummary() {
    String wechat = """
        # 标题

        > 摘要：一眼看懂冲突与变化

        正文段落。
        """;
    assertThat(HotNewsMarkdownUtil.extractWechatDigestLine(wechat))
        .isEqualTo("一眼看懂冲突与变化");
  }

  @Test
  void ensureWechatDigestLineNormalizesExistingLineAfterTitle() {
    String wechat = """
        # 热点标题

        摘要：没有引用前缀的旧写法

        正文第一段。
        """;
    String ensured = HotNewsMarkdownUtil.ensureWechatDigestLine(wechat, "兜底摘要");
    assertThat(ensured).startsWith("# 热点标题");
    assertThat(ensured).contains("> 摘要：没有引用前缀的旧写法");
    assertThat(HotNewsMarkdownUtil.extractWechatDigestLine(ensured))
        .isEqualTo("没有引用前缀的旧写法");
    // 旧行已移除，只保留规范行一次
    assertThat(ensured.indexOf("摘要：")).isEqualTo(ensured.lastIndexOf("摘要："));
  }

  @Test
  void ensureWechatDigestLineInsertsFallbackWhenMissing() {
    String wechat = """
        # 热点标题

        正文第一段。
        """;
    String ensured = HotNewsMarkdownUtil.ensureWechatDigestLine(wechat, "从骨架论点兜底");
    assertThat(ensured).contains("# 热点标题");
    assertThat(ensured).contains("> 摘要：从骨架论点兜底");
    int titleAt = ensured.indexOf("# 热点标题");
    int digestAt = ensured.indexOf("> 摘要：");
    int bodyAt = ensured.indexOf("正文第一段");
    assertThat(digestAt).isGreaterThan(titleAt);
    assertThat(bodyAt).isGreaterThan(digestAt);
  }

  @Test
  void ensureWechatDigestLineTruncatesToWechatLimit() {
    String longFallback = "字".repeat(HotNewsMarkdownUtil.WECHAT_DIGEST_MAX_CHARS + 40);
    String ensured = HotNewsMarkdownUtil.ensureWechatDigestLine("# 标题\n\n正文", longFallback);
    String digest = HotNewsMarkdownUtil.extractWechatDigestLine(ensured);
    assertThat(digest.codePointCount(0, digest.length()))
        .isEqualTo(HotNewsMarkdownUtil.WECHAT_DIGEST_MAX_CHARS);
  }

  @Test
  void imageMarkdownHelpersRoundTrip() {
    String draft = """
        **插图位置：封面图**

        ```text
        封面提示词
        ```

        ![](/api/images/abc123)
        """;
    var hit = HotNewsMarkdownUtil.findPrompts(draft).get(0);
    String image = HotNewsMarkdownUtil.imageMarkdownAfter(draft, hit.end());
    assertThat(image).contains("/api/images/abc123");
    assertThat(HotNewsMarkdownUtil.imageIdFromMarkdown(image)).isEqualTo("abc123");
    String stripped = HotNewsMarkdownUtil.stripImageAfterPrompt(draft, hit);
    assertThat(stripped).contains("封面提示词");
    assertThat(stripped).doesNotContain("![](/api/images/abc123)");
  }

  @Test
  void finalizeEvidenceMarkdown_expandsTokenAndStripsExternalUrls() {
    String fileId = "11111111-1111-1111-1111-111111111111";
    String markdown = """
        关键数字上升。

        来源简称：https://news.example/report
        [[HOT_NEWS_EVIDENCE:%s]]

        详见 [报道](https://news.example/report) 与 https://other.example/x
        """.formatted(fileId);
    String finalized = HotNewsMarkdownUtil.finalizeEvidenceMarkdown(markdown);
    assertThat(finalized).contains("![来源截图](/api/files/" + fileId + ")");
    assertThat(finalized).contains("来源简称");
    assertThat(finalized).doesNotContain("https://news.example/report");
    assertThat(finalized).doesNotContain("https://other.example/x");
    assertThat(finalized).contains("报道");
    assertThat(finalized).contains("/api/files/" + fileId);
  }

  @Test
  void insertEvidenceShot_usesAnchorOrAppends() {
    String fileId = "22222222-2222-2222-2222-222222222222";
    String token = "[[HOT_NEWS_EVIDENCE:" + fileId + "]]";
    String withAnchor = HotNewsMarkdownUtil.insertEvidenceShot(
        "前文\n来源：财联社\n后文", "来源：财联社", "财联社", fileId);
    assertThat(withAnchor).contains("来源：财联社\n" + token + "\n");
    assertThat(withAnchor.indexOf("来源：财联社"))
        .isEqualTo(withAnchor.lastIndexOf("来源：财联社"));
    String appended = HotNewsMarkdownUtil.insertEvidenceShot("只有正文", "不存在的锚点", "媒体", fileId);
    assertThat(appended).endsWith(token + "\n");
    assertThat(appended).contains("来源：媒体\n" + token);
  }

  @Test
  void insertEvidenceShot_snapsShortAnchorToSentenceEndAndStripsCaptionPrefix() {
    String fileId = "44444444-4444-4444-4444-444444444444";
    String token = "[[HOT_NEWS_EVIDENCE:" + fileId + "]]";
    String draft = "787%是盘中最高，150.58元。还有财联社发稿那一刻的120元。";
    String result = HotNewsMarkdownUtil.insertEvidenceShot(
        draft,
        "150.58元",
        "来源：财联社《中一签最多赚6.6万元，A股再现“肉签”》",
        fileId);
    assertThat(result).contains("150.58元。\n\n来源：财联社《中一签最多赚6.6万元，A股再现“肉签”》\n" + token);
    assertThat(result).contains(token + "\n还有财联社发稿那一刻的120元。");
    assertThat(result).doesNotContain("来源：来源：");
    assertThat(result).doesNotContain("元\n\n来源：");
  }

  @Test
  void normalizeEvidenceCaption_stripsSourcePrefix() {
    assertThat(HotNewsMarkdownUtil.normalizeEvidenceCaption("来源：财联社"))
        .isEqualTo("财联社");
    assertThat(HotNewsMarkdownUtil.normalizeEvidenceCaption("来源:  上海证券报"))
        .isEqualTo("上海证券报");
    assertThat(HotNewsMarkdownUtil.normalizeEvidenceCaption("  ")).isEqualTo("参考资料");
  }

  @Test
  void draftImagesFollowPrompts_allowsEvidenceShotsOutsidePromptBlocks() {
    String evidenceId = "33333333-3333-3333-3333-333333333333";
    String draft = """
        **插图位置：封面图**

        ```text
        封面提示词
        ```

        ![](/api/images/cover1)

        正文段落。

        来源：财联社
        ![来源截图](/api/files/%s)

        **插图位置：插图 1｜对比**

        ```text
        正文图一
        ```

        ![](/api/images/inline1)
        """.formatted(evidenceId);
    assertThat(HotNewsMarkdownUtil.isEvidenceImageMarkdown(
        "![来源截图](/api/files/" + evidenceId + ")")).isTrue();
    assertThat(HotNewsMarkdownUtil.draftImagesFollowPrompts(draft)).isTrue();
  }

  @Test
  void draftImagesFollowPrompts_rejectsOrphanGeneratedImages() {
    String draft = """
        正文里突然出现配图：

        ![](/api/images/orphan)
        """;
    assertThat(HotNewsMarkdownUtil.draftImagesFollowPrompts(draft)).isFalse();
  }
}
