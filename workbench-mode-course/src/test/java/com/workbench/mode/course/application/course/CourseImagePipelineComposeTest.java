package com.workbench.mode.course.application.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CourseImagePipelineComposeTest {
  @Test
  void compose_replaces_cover_and_inlines() {
    String wechat = """
        # Title

        > 系列：AI Demo｜难度：零基础｜预计阅读：5 分钟
        >
        > 本课覆盖：demo
        >
        > 摘要：demo

        [[COURSE_COVER]]

        ## 01｜开场

        段落一内容足够长用于分布到正文插图位置附近。

        段落二内容足够长用于分布到正文插图位置附近。

        ## 本课小结
        - ok

        ## 下节课预告
        - next
        """;
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "sequenceNo", 1, "publicUrl", "/api/images/c1"),
        Map.of("kind", "inline", "sequenceNo", 2, "inlineNo", 1, "publicUrl", "/api/images/i1"));
    String composed = CourseImagePipeline.composeWechatImages(wechat, images);
    assertThat(composed).contains("![课程封面](/api/images/c1)");
    assertThat(composed).contains("![插图 1](/api/images/i1)");
    assertThat(composed).doesNotContain("[[COURSE_COVER]]");
    assertThat(composed.indexOf("![插图 1](")).isLessThan(composed.indexOf("本课小结"));
  }

  @Test
  void compose_strips_malformed_illustration_residue() {
    String wechat = """
        # 让未训练的网络完成第一次预测

        > 系列：AI 模型原理｜难度：零基础｜预计阅读：5 分钟
        >
        > 本课覆盖：前向计算
        >
        > 摘要：读懂输出形状

        [[COURSE_COVER]]

        [[COURSE_IMAGE_1]]

        ## 开场与本课目标

        上一课我们把 MNIST 放到本地，本课先完成一次预测。这段要足够长，方便内文插图分布到段落附近。

        插图位置：概念图，图片进入未训练网络后得到十个分数并取最高分

        提示词：深海蓝三维科技空间，Apple Keynote 风格。左侧玻璃卡片，右侧分数条。16:9比例 --v 6

        ## 核心概念

        一批图片形状是 [64, 1, 28, 28]，Flatten 后变成 [64, 784]，最后输出 [64, 10]。这段也要足够长。

        [[COURSE_IMAGE_2]]

        插图位置：形状随网络层层变化的流程示意图

        提示词：四块悬浮玻璃面板从左到右标注形状。16:9比例 --v 6

        ## 本课小结

        只做了前向计算。

        ## 下节课预告

        下一课讲损失。
        """;
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "sequenceNo", 1, "publicUrl", "/api/files/cover"),
        Map.of("kind", "inline", "sequenceNo", 2, "inlineNo", 1, "publicUrl", "/api/files/i1"),
        Map.of("kind", "inline", "sequenceNo", 3, "inlineNo", 2, "publicUrl", "/api/files/i2"));

    String composed = CourseImagePipeline.composeWechatImages(wechat, images);

    assertThat(composed).contains("![课程封面](/api/files/cover)");
    assertThat(composed).contains("![插图 1](/api/files/i1)");
    assertThat(composed).contains("![插图 2](/api/files/i2)");
    assertThat(composed).doesNotContain("[[COURSE_COVER]]");
    assertThat(composed).doesNotContain("[[COURSE_IMAGE_");
    assertThat(composed).doesNotContain("插图位置");
    assertThat(composed).doesNotContain("提示词：");
    assertThat(composed).doesNotContain("未选择推荐风格");
    // 正文里的「插图」叙述不应误伤；本例无此类句子，只确认小结仍在
    assertThat(composed).contains("本课小结");
  }

  @Test
  void compose_strips_bare_midjourney_paragraph_between_prose() {
    // 截图形态：转换模型拆掉围栏后，裸提示词夹在两段正文之间
    String wechat = """
        # 检索增强生成

        > 系列：AI 基础｜难度：零基础｜预计阅读：8 分钟
        >
        > 本课覆盖：检索阶段
        >
        > 摘要：把索引与回答连起来

        [[COURSE_COVER]]

        ## 01｜检索阶段

        这些阶段最后都要回到原文片段，这段要足够长方便插图分布到段落附近，再多写几个字。

        深海蓝三维科技空间，Apple Keynote 风格，高端 SaaS 仪表盘美学，悬浮玻璃结构有厚度和透视，强霓虹边缘光与体积光，科技网格与粒子，环形能量轨道。画面中央是三个依次排列的纵向悬浮闸门。16:9比例 --v 6

        正确回答需要同时满足三个条件，这段也要足够长用来落第二张图，再多写几个字。

        ## 本课小结

        检索要回到原文。

        ## 下节课预告

        下一课讲生成。
        """;
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "sequenceNo", 1, "publicUrl", "/api/files/cover"),
        Map.of("kind", "inline", "sequenceNo", 2, "inlineNo", 1, "publicUrl", "/api/files/i1"));

    String composed = CourseImagePipeline.composeWechatImages(wechat, images);

    assertThat(composed).contains("![课程封面](/api/files/cover)");
    assertThat(composed).contains("![插图 1](/api/files/i1)");
    assertThat(composed).contains("这些阶段最后都要回到原文片段");
    assertThat(composed).contains("正确回答需要同时满足三个条件");
    assertThat(composed).doesNotContain("深海蓝三维科技空间");
    assertThat(composed).doesNotContain("--v 6");
    assertThat(composed).doesNotContain("Apple Keynote");
  }

  @Test
  void compose_strips_illustration_caption_headings_with_prompt_quotes() {
    // 对齐 Python _normalize_wechat_images：### 插图 N + 引用块提示词应删掉
    String wechat = """
        # 让未训练的网络完成第一次预测

        > 系列：AI 模型原理｜难度：零基础｜预计阅读：5 分钟
        >
        > 本课覆盖：前向计算
        >
        > 摘要：读懂输出形状

        [[COURSE_COVER]]

        ## 01｜核心概念

        前向计算把图片变成类别分数。这段要足够长，方便内文插图分布到段落附近，再多写几个字。

        ### 插图 1：2×2 灰度图前向计算

        > 深海蓝三维科技空间，Apple Keynote 风格，悬浮玻璃面板，16:9比例 --v 6

        分数可以是负数。这段也要足够长，方便第二张图落点，再多写几个字。

        ### 插图 2：类别分数比较

        > 两个悬浮节点指向三个输出分数，最大项高亮，16:9比例 --v 6

        ## 本课小结

        只做了前向计算。

        ## 下节课预告

        下一课讲损失。
        """;
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "sequenceNo", 1, "publicUrl", "/api/files/cover"),
        Map.of("kind", "inline", "sequenceNo", 2, "inlineNo", 1, "publicUrl", "/api/files/i1"),
        Map.of("kind", "inline", "sequenceNo", 3, "inlineNo", 2, "publicUrl", "/api/files/i2"));

    String composed = CourseImagePipeline.composeWechatImages(wechat, images);

    assertThat(composed).contains("![课程封面](/api/files/cover)");
    assertThat(composed).contains("![插图 1](/api/files/i1)");
    assertThat(composed).doesNotContain("### 插图");
    assertThat(composed).doesNotContain("深海蓝三维科技空间");
    assertThat(composed).doesNotContain("--v 6");
    assertThat(composed).contains("本课小结");
  }

  @Test
  void replace_leftover_tokens_converts_heading_and_bare_tokens() {
    // checklist I3：### [[COURSE_IMAGE_N]] / 残留 token 在 compose 前扫尾
    String wechat = """
        # Title

        > 系列：AI Demo｜难度：零基础｜预计阅读：5 分钟
        >
        > 本课覆盖：demo
        >
        > 摘要：demo

        [[COURSE_COVER]]

        ## 01｜开场

        段落一内容足够长用于分布到正文插图位置附近，避免被判过短，再多写几个字。

        ### [[COURSE_IMAGE_1]]

        段落二内容足够长用于分布到正文插图位置附近，避免被判过短，再多写几个字。

        #### [[COURSE_IMAGE_2]]

        段落三内容足够长，越界 token 应被删除。

        [[COURSE_IMAGE_9]]

        ## 本课小结
        - ok

        ## 下节课预告
        - next
        """;
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "sequenceNo", 1, "publicUrl", "/api/files/cover"),
        Map.of("kind", "inline", "sequenceNo", 2, "inlineNo", 1, "publicUrl", "/api/files/i1"),
        Map.of("kind", "inline", "sequenceNo", 3, "inlineNo", 2, "publicUrl", "/api/files/i2"));

    String swept = CourseImagePipeline.replaceLeftoverImageTokens(wechat, images);
    assertThat(swept).contains("![插图 1](/api/files/i1)");
    assertThat(swept).contains("![插图 2](/api/files/i2)");
    assertThat(swept).contains("![课程封面](/api/files/cover)");
    assertThat(swept).doesNotContain("[[COURSE_IMAGE_");
    assertThat(swept).doesNotContain("### [[COURSE_IMAGE");

    String composed = CourseImagePipeline.composeWechatImages(swept, images);
    assertThat(composed).doesNotContain("[[COURSE_");
    assertThat(composed).contains("![插图 1](/api/files/i1)");
    assertThat(composed).contains("本课小结");
  }

  @Test
  void compose_dedupes_api_files_images_already_applied() {
    // 模拟 applyImageResult 已把 token 换成 /api/files 引用后，再进 compose
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

    assertThat(composed).contains("![课程封面](/api/files/new-cover)");
    assertThat(composed).contains("/api/files/new-i1");
    assertThat(composed).contains("/api/files/new-i2");
    assertThat(composed).contains("/api/files/new-i3");
    assertThat(composed).doesNotContain("/api/files/old-");
    long inlineCount = Pattern.compile("(?m)^!\\[插图\\s*\\d+")
        .matcher(composed)
        .results()
        .count();
    assertThat(inlineCount).isEqualTo(3);
  }

  @Test
  void diversity_brief_rotates_layout_and_palette_by_sequence() {
    var first = new CourseImagePipeline.ImagePlanItem(
        2, 1, "inline", "插图位置：对比", "对比图", "[[COURSE_IMAGE_1]]", "2048x1152", "image_generation");
    var second = new CourseImagePipeline.ImagePlanItem(
        3, 2, "inline", "插图位置：流程", "流程图", "[[COURSE_IMAGE_2]]", "2048x1152", "image_generation");
    String a = CourseImagePipeline.diversityBrief(first, false);
    String b = CourseImagePipeline.diversityBrief(second, false);
    assertThat(a).contains("强制差异化").contains("严禁默认套用深海蓝 Keynote");
    assertThat(b).contains("强制差异化");
    assertThat(a).isNotEqualTo(b);
    assertThat(CourseImagePipeline.trimSkillPrompt(
            "视觉规范：\n1. 使用深海蓝三维科技空间、Apple Keynote 风格。\n2. 检查尺寸。\n", false))
        .doesNotContain("深海蓝")
        .doesNotContain("Keynote")
        .contains("检查尺寸");
  }
}
