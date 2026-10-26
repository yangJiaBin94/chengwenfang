package com.workbench.mode.course.application.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CourseMarkdownUtilTest {

  @Test
  void sanitize_adds_heading_when_missing() {
    String raw = "上一课把数据准备好了。这一课让网络做出第一次预测。";

    String cleaned = CourseMarkdownUtil.sanitizeDraftSection(raw, "draft_opening", "开场与本课目标");

    assertThat(cleaned).startsWith("## 开场与本课目标");
    assertThat(cleaned).contains("第一次预测");
  }

  @Test
  void sanitize_cuts_summary_heading_out_of_core_section() {
    String raw = """
        ## 核心原理

        输出是一组类别分数。

        ## 本课小结

        这里不该出现在原理段。
        """;

    String cleaned = CourseMarkdownUtil.sanitizeDraftSection(raw, "draft_core", "核心原理");

    assertThat(cleaned).startsWith("## 核心原理");
    assertThat(cleaned).contains("类别分数");
    assertThat(cleaned).doesNotContain("本课小结");
    assertThat(cleaned).doesNotContain("不该出现");
  }

  @Test
  void sanitize_keeps_model_heading_when_already_present() {
    String raw = """
        ## 核心概念

        前向计算把图片变成一组类别分数。
        """;

    String cleaned = CourseMarkdownUtil.sanitizeDraftSection(
        raw, "draft_core", "让未训练的网络完成第一次预测的核心原理");

    assertThat(cleaned).startsWith("## 核心概念");
    assertThat(cleaned).contains("前向计算");
  }

  @Test
  void lesson_body_count_excludes_all_fenced_blocks_and_prompt_markers() {
    String markdown = """
        # 标题

        正文甲乙丙。

        **插图位置：对比图**

        ```text
        这是规范插图提示词，很长很长很长很长很长很长。
        ```

        正文丁戊己。

        ```python
        print("这段 python 不计入正文")
        ```

        ```bash
        python update_demo.py
        ```

        ~~~json
        {"skip": true}
        ~~~

        详见[说明](https://example.com/very-long-url-path)。
        """;

    int body = CourseMarkdownUtil.lessonBodyCharCount(markdown);

    // 仅标题 + 两句正文 + 链接可见文案「说明」
    assertThat(body).isEqualTo(CourseMarkdownUtil.lessonBodyCharCount("""
        # 标题

        正文甲乙丙。

        正文丁戊己。

        详见说明。
        """));
    assertThat(body).isLessThan(40);
  }

  @Test
  void sanitize_full_draft_strips_compress_reasoning_around_real_markdown() {
    String dirty = """
        我们需要回答用户请求。用户要求：编辑下面的完整课程原稿。

        # 让未训练的网络完成第一次预测

        ## 开场与本课目标

        本课完成一次前向计算。

        ## 本课小结

        通路已经打通。

        ## 下节课预告

        下一课看损失函数。

        这个正文大概不到 700 字。很好。
        """;

    String cleaned = CourseMarkdownUtil.sanitizeFullDraft(dirty);
    cleaned = CourseMarkdownUtil.stripInlineReasoningParagraphs(cleaned);
    cleaned = CourseMarkdownUtil.sanitizeFullDraft(cleaned);

    assertThat(cleaned).startsWith("# 让未训练的网络完成第一次预测");
    assertThat(cleaned).doesNotContain("我们需要回答用户");
    assertThat(cleaned).doesNotContain("大概不到 700 字");
    assertThat(cleaned).contains("本课小结");
    assertThat(cleaned).contains("下节课预告");
  }
}
