package com.workbench.mode.course.application.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DraftPublishableAndWechatTokenTest {

  @Test
  void finalize_publishable_draft_cleans_dirty_compression_output() throws Exception {
    String dirty = new String(
        getClass().getResourceAsStream("/fixtures/dirty-course-draft-9e7b8b6e.md").readAllBytes(),
        StandardCharsets.UTF_8);

    String cleaned = CourseMarkdownUtil.finalizePublishableDraft(dirty, 3, 5, "让未训练的网络完成第一次预测");

    assertThat(cleaned).doesNotContain("正文要压缩");
    assertThat(cleaned).doesNotContain("现在，我们还需要");
    assertThat(cleaned).doesNotContain("如果检查器期望");
    assertThat(cleaned).doesNotContain("（插图位置");
    assertThat(CourseMarkdownUtil.countInlinePrompts(cleaned)).isBetween(3, 5);
    // 有效提示词围栏非空
    for (CourseMarkdownUtil.PromptHit hit : CourseMarkdownUtil.findPrompts(cleaned)) {
      if (!CourseMarkdownUtil.isCoverPrompt(hit.marker(), hit.prompt())) {
        assertThat(hit.prompt()).isNotBlank();
      }
    }
    CourseMarkdownUtil.WechatSource source = CourseMarkdownUtil.prepareWechatSource(cleaned);
    assertThat(source.imageTokens().size()).isBetween(3, 5);
  }

  @Test
  void prepare_wechat_source_skips_empty_prompt_fences() {
    String draft = """
        # 课

        **插图位置：空块**

        ```text

        ```

        **插图位置：实块**

        ```text
        一张解释前向计算的概念图，16:9。
        ```

        ## 本课小结
        x

        ## 下节课预告
        y
        """;
    CourseMarkdownUtil.WechatSource source = CourseMarkdownUtil.prepareWechatSource(draft);
    assertThat(source.imageTokens()).containsExactly("[[COURSE_IMAGE_1]]");
    assertThat(source.prepared()).contains("[[COURSE_IMAGE_1]]");
  }

  @Test
  void compose_still_dedupes_api_files_after_token_path() {
    String wechat = """
        # Title

        > 系列：AI Demo｜难度：零基础｜预计阅读：5 分钟
        >
        > 本课覆盖：demo
        >
        > 摘要：demo

        ![封面图：Title](/api/files/old-cover)

        ## 01｜开场

        段落一内容足够长用于分布到正文插图位置附近，避免被判过短，再多写几个字。

        ![插图 1](/api/files/old-i1)

        段落二内容足够长用于分布到正文插图位置附近，避免被判过短，再多写几个字。

        ![插图 2](/api/files/old-i2)

        段落三内容足够长用于分布到正文插图位置附近，避免被判过短，再多写几个字。

        ![插图 3](/api/files/old-i3)

        ## 本课小结
        - ok

        ## 下节课预告
        - next
        """;
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "sequenceNo", 1, "publicUrl", "/api/files/new-cover"),
        Map.of("kind", "inline", "sequenceNo", 2, "inlineNo", 1, "publicUrl", "/api/files/new-i1"),
        Map.of("kind", "inline", "sequenceNo", 3, "inlineNo", 2, "publicUrl", "/api/files/new-i2"),
        Map.of("kind", "inline", "sequenceNo", 4, "inlineNo", 3, "publicUrl", "/api/files/new-i3"));
    String composed = CourseImagePipeline.composeWechatImages(wechat, images);
    assertThat(composed).doesNotContain("/api/files/old-");
    assertThat(composed).contains("/api/files/new-i1");
  }
}
