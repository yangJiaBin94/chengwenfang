package com.chengwenfang.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 插图提示词权威格式规范化与裸残留清理测试。
 */
class IllustrationPromptFormatTest {

  @Test
  void canonicalize_converts_bare_marker_and_prompt_lines() {
    String dirty = """
        前文说明检索阶段。

        插图位置：三道闸门

        提示词：深海蓝三维科技空间，Apple Keynote 风格，高端 SaaS 仪表盘美学，16:9比例 --v 6

        后文继续讲解。
        """;
    String cleaned = IllustrationPromptFormat.canonicalizeIllustrationPrompts(dirty);
    assertTrue(cleaned.contains("**插图位置：三道闸门**"));
    assertTrue(cleaned.contains("```text"));
    assertTrue(cleaned.contains("深海蓝三维科技空间"));
    assertFalse(cleaned.contains("提示词：深海蓝"));
  }

  @Test
  void canonicalize_converts_caption_heading_quote_blocks() {
    String dirty = """
        ### 插图 1：离线索引

        > 深海蓝三维科技空间，Apple Keynote 风格，悬浮玻璃面板，16:9比例 --v 6

        正文继续。
        """;
    String cleaned = IllustrationPromptFormat.canonicalizeIllustrationPrompts(dirty);
    assertTrue(cleaned.contains("**插图位置：离线索引**"));
    assertTrue(cleaned.contains("```text"));
    assertFalse(cleaned.contains("### 插图"));
    assertFalse(cleaned.contains("> 深海蓝"));
  }

  @Test
  void stripBare_removes_midjourney_paragraph_between_prose() {
    String wechat = """
        这些阶段最后都要回到原文片段。

        深海蓝三维科技空间，Apple Keynote 风格，高端 SaaS 仪表盘美学，悬浮玻璃结构有厚度和透视，强霓虹边缘光与体积光，科技网格与粒子，环形能量轨道。画面中央是三个依次排列的纵向悬浮闸门。16:9比例 --v 6

        正确回答需要同时满足三个条件。
        """;
    String cleaned = IllustrationPromptFormat.stripBareImagePromptParagraphs(wechat);
    assertTrue(cleaned.contains("这些阶段最后都要回到原文片段"));
    assertTrue(cleaned.contains("正确回答需要同时满足三个条件"));
    assertFalse(cleaned.contains("深海蓝三维科技空间"));
    assertFalse(cleaned.contains("--v 6"));
  }

  @Test
  void hasBareImagePromptLeak_detects_orphan_midjourney_paragraph() {
    String wechat = """
        正文一段足够长的说明。

        深海蓝三维科技空间，Apple Keynote 风格，高端 SaaS 仪表盘美学，悬浮玻璃结构，16:9比例 --v 6

        更多正文。
        """;
    assertTrue(IllustrationPromptFormat.hasBareImagePromptLeak(wechat));
    assertFalse(IllustrationPromptFormat.hasBareImagePromptLeak("正文一段，没有生图描述。"));
  }

  @Test
  void canonicalize_preserves_standard_blocks() {
    String standard = """
        **插图位置：封面图**

        ```text
        课程封面提示词
        ```
        """;
    String cleaned = IllustrationPromptFormat.canonicalizeIllustrationPrompts(standard);
    assertTrue(cleaned.contains("**插图位置：封面图**"));
    assertTrue(cleaned.contains("课程封面提示词"));
  }
}
