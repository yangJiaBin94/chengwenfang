package com.chengwenfang.mode.course.application.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.skill.facade.SkillFacade;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CourseMarkdownUtilParityTest {

  @Test
  void sanitize_full_draft_drops_compress_reasoning_preamble() {
    String raw = """
        我需要输出压缩后的完整课程原稿 Markdown，保留本课小结与下节课预告。

        # 第一次预测

        ## 开场与本课目标

        正文开始。

        ## 本课小结

        收束。

        ## 下节课预告

        下一课。
        """;

    String cleaned = CourseMarkdownUtil.sanitizeFullDraft(raw);

    assertThat(cleaned).startsWith("# 第一次预测");
    assertThat(cleaned).doesNotContain("我需要输出");
    assertThat(CourseMarkdownUtil.countOccurrences(cleaned, "本课小结")).isEqualTo(1);
  }

  @Test
  void sanitize_full_draft_drops_trailing_reasoning_after_preview() {
    String raw = """
        # 第一次预测

        ## 开场与本课目标

        正文开始。

        ## 本课小结

        收束本课。

        ## 下节课预告

        下一课讲损失函数。

        现在计算正文字数。我们去掉插图位置、提示词、代码块？
        开场与目标：约 80 字。
        本课小结：约 80 字。
        下节课预告：约 40 字。
        总计约 720 字。
        Windows/macOS 差异保留了。本课小结、下节课预告保留了。
        最终输出。确保只输出 Markdown。
        """;

    String cleaned = CourseMarkdownUtil.sanitizeFullDraft(raw);

    assertThat(cleaned).contains("## 本课小结");
    assertThat(cleaned).contains("## 下节课预告");
    assertThat(cleaned).contains("下一课讲损失函数");
    assertThat(cleaned).doesNotContain("现在计算");
    assertThat(cleaned).doesNotContain("总计约");
    assertThat(cleaned).doesNotContain("最终输出");
    assertThat(CourseMarkdownUtil.countOccurrences(cleaned, "本课小结")).isEqualTo(1);
    assertThat(CourseMarkdownUtil.countOccurrences(cleaned, "下节课预告")).isEqualTo(1);
  }

  @Test
  void restore_missing_prompt_blocks_inserts_before_summary() {
    String withPrompt = """
        # 课

        ## 案例

        **插图位置：流程图**

        ```text
        画一张前向计算流程图
        ```

        ## 本课小结

        小结。

        ## 下节课预告

        预告。
        """;
    List<CourseMarkdownUtil.PromptHit> required = CourseMarkdownUtil.findPrompts(withPrompt);
    String compressed = """
        # 课

        ## 案例

        只剩文字。

        ## 本课小结

        小结。

        ## 下节课预告

        预告。
        """;

    String restored = CourseMarkdownUtil.restoreMissingPromptBlocks(compressed, required);

    assertThat(restored).contains("画一张前向计算流程图");
    assertThat(restored.indexOf("画一张前向计算流程图"))
        .isLessThan(restored.indexOf("本课小结"));
  }

  @Test
  void ensure_limit_distribute_inline_prompts() {
    String draft = """
        # 课

        ## 原理

        说明。

        ### 步骤一

        细节。

        ### 步骤二

        细节。

        ## 本课小结

        小结。

        ## 下节课预告

        预告。
        """;

    String ensured = CourseMarkdownUtil.ensureInlinePromptCount(draft, 2, "第一次预测", List.of("前向", "分数"));
    assertThat(CourseMarkdownUtil.countInlinePrompts(ensured)).isEqualTo(2);
    assertThat(ensured).contains("解释「前向」");
    assertThat(ensured).doesNotContain("为课程《第一次预测》");
    assertThat(ensured).contains("禁止在画面上写课程标题");

    String limited = CourseMarkdownUtil.limitInlinePrompts(ensured, 1);
    assertThat(CourseMarkdownUtil.countInlinePrompts(limited)).isEqualTo(1);

    String withTwo = CourseMarkdownUtil.ensureInlinePromptCount(draft, 2, "第一次预测", List.of("A", "B"));
    String distributed = CourseMarkdownUtil.distributeInlinePrompts(withTwo);
    assertThat(CourseMarkdownUtil.countInlinePrompts(distributed)).isEqualTo(2);

    List<String> subjects = CourseMarkdownUtil.extractIllustrationSubjects(draft, "第一次预测");
    assertThat(subjects).contains("原理", "步骤一", "步骤二");
  }

  @Test
  void compression_target_is_stricter_than_max() {
    assertThat(CourseMarkdownUtil.compressionTarget(6000, 4000)).isLessThan(4000);
    assertThat(CourseMarkdownUtil.compressionTarget(4000, 4000)).isLessThanOrEqualTo(4000 - 180);
  }

  @Test
  void wechat_converter_sends_system_prompt() {
    AiTextFacade ai = mock(AiTextFacade.class);
    SkillFacade skills = mock(SkillFacade.class);
    when(skills.composePromptFromBindings(any())).thenReturn("作者风格：口语");
    when(ai.completeText(any())).thenReturn(TextCompleteResult.builder()
        .text("""
            # 标题

            转换后的段落，足够长以通过保留率检查。转换后的段落，足够长以通过保留率检查。
            """)
        .build());

    CourseGenerationContext ctx = new CourseGenerationContext(
        "c1",
        "课",
        "摘要",
        "s1",
        "系列",
        1,
        "初级",
        "4000-6000",
        "up-to-3",
        4000,
        0,
        3,
        "",
        "",
        "",
        "",
        List.of(),
        "draft",
        "wechat");
    String source = ("短正文也要凑够长度。").repeat(80)
        + "\n\n## 本课小结\n\nx\n\n## 下节课预告\n\ny\n";
    new WechatChunkConverter(ai, skills).convert(source, ctx, Map.of(), null);

    ArgumentCaptor<TextCompleteRequest> captor = ArgumentCaptor.forClass(TextCompleteRequest.class);
    verify(ai).completeText(captor.capture());
    assertThat(captor.getValue().getSystemPrompt()).contains("你是严谨的中文 AI 课程作者");
    assertThat(captor.getValue().getSystemPrompt()).contains("知识库写作规范");
    assertThat(captor.getValue().getSystemPrompt()).contains("作者风格");
    assertThat(captor.getValue().getPrompt()).contains("只输出本段转换结果");
    assertThat(captor.getValue().getPrompt()).contains("不删减也不扩写");
    assertThat(captor.getValue().getPrompt()).contains("也不得超过原稿本段的 135%");
    assertThat(captor.getValue().getPrompt()).contains("不得超过本次课程上限 4000 字");
    assertThat(captor.getValue().getPrompt()).contains("上轮检查反馈：无，这是首次转换");
    assertThat(captor.getValue().getPrompt()).doesNotContain("知识库写作规范");
  }

  @Test
  void wechat_chunk_rejects_over_expansion_and_falls_back() {
    String source = ("短正文也要凑够长度。").repeat(40)
        + "\n\n[[COURSE_IMAGE_1]]\n\n## 本课小结\n\nx\n\n## 下节课预告\n\ny\n";
    String bloated = ("扩写扩写扩写扩写。").repeat(400) + "\n\n[[COURSE_IMAGE_1]]\n";
    assertThat(CourseMarkdownUtil.wechatChunkComplete(source, bloated)).isFalse();

    AiTextFacade ai = mock(AiTextFacade.class);
    SkillFacade skills = mock(SkillFacade.class);
    when(skills.composePromptFromBindings(any())).thenReturn("");
    when(ai.completeText(any())).thenReturn(TextCompleteResult.builder().text(bloated).build());

    CourseGenerationContext ctx = new CourseGenerationContext(
        "c1", "课", "摘要", "s1", "系列", 1, "零基础", "2000-4000", "up-to-3",
        4000, 0, 3, "", "", "", "", List.of(), "draft", "wechat");
    WechatChunkConverter.Result result = new WechatChunkConverter(ai, skills).convert(
        source, ctx, Map.of(), null);

    assertThat(result.wechat()).contains("[[COURSE_IMAGE_1]]");
    assertThat(result.wechat()).contains("短正文也要凑够长度");
  }

  @Test
  void wechat_converter_receives_repair_feedback() {
    AiTextFacade ai = mock(AiTextFacade.class);
    SkillFacade skills = mock(SkillFacade.class);
    when(skills.composePromptFromBindings(any())).thenReturn("");
    when(ai.completeText(any())).thenReturn(TextCompleteResult.builder()
        .text("# 标题\n\n" + "转换后的段落，足够长以通过保留率检查。".repeat(6))
        .build());

    CourseGenerationContext ctx = new CourseGenerationContext(
        "c1", "课", "摘要", "s1", "系列", 1, "零基础", "4000-6000", "up-to-3",
        4000, 0, 3, "", "", "", "", List.of(), "draft", "wechat");
    String source = ("短正文也要凑够长度。").repeat(80)
        + "\n\n## 本课小结\n\nx\n\n## 下节课预告\n\ny\n";
    new WechatChunkConverter(ai, skills).convert(
        source, ctx, Map.of(), null, "第 1 轮检查未通过。COURSE_BODY_TOO_LONG：超长。");

    ArgumentCaptor<TextCompleteRequest> captor = ArgumentCaptor.forClass(TextCompleteRequest.class);
    verify(ai).completeText(captor.capture());
    assertThat(captor.getValue().getPrompt()).contains("第 1 轮检查未通过。COURSE_BODY_TOO_LONG");
  }

  @Test
  void section_sanitize_cuts_summary_heading_without_hard_reject() {
    String dirty = """
        ## 第一次预测的核心原理

        好的，用户要我写一个章节。输出是一组类别分数。

        ## 本课小结

        这里不该出现在原理段。
        """;
    // 与原版一致：不对「任务复述」硬拒收，只裁掉越界的小结标题。
    assertThat(CourseMarkdownUtil.sectionRejectionReason(dirty, "draft_core")).isNull();
    String cleaned = CourseMarkdownUtil.sanitizeDraftSection(
        dirty, "draft_core", "第一次预测的核心原理");
    assertThat(cleaned).contains("用户要我写");
    assertThat(cleaned).contains("类别分数");
    assertThat(cleaned).doesNotContain("本课小结");
    assertThat(cleaned).doesNotContain("不该出现");
  }

  @Test
  void dynamic_section_budget_shrinks_when_used_chars_grow() {
    CourseGenerationContext ctx = new CourseGenerationContext(
        "c1", "课", "摘要", "s1", "系列", 1, "零基础", "4000-6000", "3-5",
        6000, 3, 5, "", "", "下一课", "下一课摘要", List.of(), "draft", "wechat");
    int[] early = ctx.sectionBudget("draft_example", 0);
    int[] late = ctx.sectionBudget("draft_example", 3500);
    assertThat(late[1]).isLessThan(early[1]);
  }

  @Test
  void compress_prompt_matches_python_wording() {
    String prompt = """
          - 保留全部插图位置与提示词，尤其是“操作截图”标记。
          - 保留课程目标、必要概念、Windows 与 macOS 的操作差异、命令、预期结果、失败处理、本课小结和下节课预告。
          - 保留 Markdown 标题结构和可运行代码，不破坏代码缩进。
          """;
    assertThat(prompt).contains("保留全部插图位置与提示词，尤其是“操作截图”标记");
    assertThat(prompt).contains("保留 Markdown 标题结构和可运行代码");
    assertThat(prompt).doesNotContain("不得改成");
  }

  @Test
  void render_plan_uses_python_section_plan_labels() {
    CourseGenerationContext ctx = new CourseGenerationContext(
        "c1", "第一次预测", "摘要", "s1", "系列", 1, "零基础", "2000-4000", "3-5",
        4000, 3, 5, "", "", "下一课", "下一课摘要", List.of(), "draft", "wechat");
    String plan = ctx.renderPlan();
    assertThat(plan).contains("- 开场与目标");
    assertThat(plan).contains("- 核心概念");
    assertThat(plan).contains("- 具体例子");
  }

  @Test
  void section_hard_max_is_metadata_only_formula() {
    CourseGenerationContext ctx = new CourseGenerationContext(
        "c1", "课", "摘要", "s1", "系列", 1, "零基础", "2000-4000", "3-5",
        4000, 3, 5, "", "", "下一课", "下一课摘要", List.of(), "draft", "wechat");
    int[] budget = ctx.sectionBudget("draft_opening", 0);
    int hard = ctx.sectionHardMax("draft_opening", 0);
    assertThat(hard).isGreaterThanOrEqualTo(budget[1]);
  }

  @Test
  void wechat_header_uses_fullwidth_separator_and_讲清() {
    CourseGenerationContext ctx = new CourseGenerationContext(
        "c1", "课", "覆盖点", "s1", "系列名", 1, "", "4000-6000", "up-to-3",
        4000, 0, 3, "", "", "下一课", "", List.of(), "draft", "wechat");
    String header = WechatChunkConverter.buildWechatHeader("# 标题\n\n正文", ctx);
    assertThat(header).contains("系列：AI 系列名｜难度：零基础｜预计阅读");
    assertThat(header).contains("讲清覆盖点");
    assertThat(header).contains(">\n> 本课覆盖：");
    assertThat(header).contains(">\n> 摘要：");
  }

  @Test
  void deterministic_wechat_chunk_joins_table_with_fullwidth_bar() {
    String converted = CourseMarkdownUtil.deterministicWechatChunk("| a | b |\n| --- | --- |\n| 1 | 2 |");
    assertThat(converted).contains("｜");
    assertThat(converted).doesNotContain(" · ");
  }

  @Test
  void deterministic_wechat_chunk_drops_fenced_prompt_body() {
    String source = """
        前文

        ```text
        深海蓝三维科技空间，Apple Keynote 风格，16:9比例 --v 6
        ```

        后文
        """;
    String converted = CourseMarkdownUtil.deterministicWechatChunk(source);
    assertThat(converted).contains("前文").contains("后文");
    assertThat(converted).doesNotContain("深海蓝三维科技空间");
    assertThat(converted).doesNotContain("--v 6");
  }

  @Test
  void screenshot_plan_uses_placeholder_not_deletion() {
    assertThat(CourseImagePipeline.requiresScreenshot("插图位置：操作截图｜登录页", "截取登录页面"))
        .isTrue();
    assertThat(CourseImagePipeline.coverPrompt(new CourseGenerationContext(
        "c1", "课", "摘要", "s1", "系列", 1, "零基础", "4000-6000", "up-to-3",
        4000, 0, 3, "", "", "", "", List.of(), "draft", "wechat")))
        .contains("2.35:1");
  }
}
