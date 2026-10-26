package com.workbench.mode.course.application.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workbench.quality.application.AuditFacadeImpl;
import com.workbench.quality.domain.engine.CourseAuditEngine;
import com.workbench.quality.domain.engine.HotArticleReviewEngine;
import com.workbench.quality.domain.engine.KnowledgeRulesEngine;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CourseGenerationValidatorsTest {
  private final CourseGenerationValidators validators =
      new CourseGenerationValidators(new AuditFacadeImpl(
          new CourseAuditEngine(),
          new KnowledgeRulesEngine(),
          new HotArticleReviewEngine(),
          null,
          null));

  @Test
  void validate_draft_rejects_short_content() {
    assertThatThrownBy(() -> validators.validateDraft("短", 4000))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GENERATED_DRAFT_INCOMPLETE");
  }

  @Test
  void image_bounds_helpers() {
    assertThat(CourseMarkdownUtil.imageBounds("3-5")).containsExactly(3, 5);
    assertThat(CourseMarkdownUtil.maxBodyChars("2000-4000")).isEqualTo(4000);
  }

  @Test
  void validate_final_accepts_valid_header_layout() {
    String filler = "开场内容要足够长，用来满足八百字门槛。".repeat(40);
    String draft = "# 课\n\n" + filler
        + "\n\n## 本课小结\n\n小结。\n\n## 下节课预告\n\n预告。\n";
    String wechat = validWechat(filler);
    CourseGenerationContext ctx = ctx(0, 3);
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "publicUrl", "/api/images/cover",
            "size", "2400x1024", "width", 2400, "height", 1024));
    assertThatCode(() -> validators.validateFinal(draft, wechat, ctx, images))
        .doesNotThrowAnyException();
  }

  @Test
  void validate_final_rejects_collapsed_header_layout() {
    String filler = "开场内容要足够长，用来满足八百字门槛。".repeat(40);
    String draft = "# 课\n\n" + filler
        + "\n\n## 本课小结\n\n小结。\n\n## 下节课预告\n\n预告。\n";
    // 覆盖与摘要挤在同一引用行 → WECHAT_HEADER_LAYOUT_INVALID
    String wechat = """
        # 课

        > 系列：AI 测试｜难度：零基础｜预计阅读：5 分钟｜本课覆盖：测试｜摘要：挤在一行

        ![课程封面](/api/images/cover)

        %s

        ## 本课小结

        小结。

        ## 下节课预告

        预告。

        ![插图 1](/api/images/i1)

        正文间隔足够长用来避免插图堆叠判定失败的可读文字填充。
        """.formatted(filler);
    CourseGenerationContext ctx = ctx(0, 3);
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "publicUrl", "/api/images/cover",
            "size", "2400x1024", "width", 2400, "height", 1024));
    assertThatThrownBy(() -> validators.validateFinal(draft, wechat, ctx, images))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WECHAT_HEADER_LAYOUT_INVALID");
  }

  @Test
  void validate_final_rejects_bare_midjourney_prompt_paragraph() {
    String filler = "开场内容要足够长，用来满足八百字门槛。".repeat(40);
    String draft = "# 课\n\n" + filler
        + "\n\n## 本课小结\n\n小结。\n\n## 下节课预告\n\n预告。\n";
    String wechat = """
        # 课

        > 系列：AI 测试｜难度：零基础｜预计阅读：5 分钟
        >
        > 本课覆盖：测试
        >
        > 摘要：本课围绕课，讲清测试。

        ![课程封面](/api/images/cover)

        %s

        深海蓝三维科技空间，Apple Keynote 风格，高端 SaaS 仪表盘美学，悬浮玻璃结构有厚度和透视，16:9比例 --v 6

        ## 本课小结

        小结。

        ## 下节课预告

        预告。

        ![插图 1](/api/images/i1)

        正文间隔足够长用来避免插图堆叠判定失败的可读文字填充。
        """.formatted(filler);
    CourseGenerationContext ctx = ctx(0, 3);
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "publicUrl", "/api/images/cover",
            "size", "2400x1024", "width", 2400, "height", 1024));
    assertThatThrownBy(() -> validators.validateFinal(draft, wechat, ctx, images))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WECHAT_IMAGE_PROMPTS_REMAIN");
  }

  @Test
  void validate_final_rejects_illustration_caption_heading_residue() {
    String filler = "开场内容要足够长，用来满足八百字门槛。".repeat(40);
    String draft = "# 课\n\n" + filler
        + "\n\n## 本课小结\n\n小结。\n\n## 下节课预告\n\n预告。\n";
    String wechat = """
        # 课

        > 系列：AI 测试｜难度：零基础｜预计阅读：5 分钟
        >
        > 本课覆盖：测试
        >
        > 摘要：本课围绕课，讲清测试。

        ![课程封面](/api/images/cover)

        %s

        ### 插图 1：2×2 灰度图前向计算

        > 深海蓝三维科技空间，Apple Keynote 风格，16:9比例 --v 6

        ## 本课小结

        小结。

        ## 下节课预告

        预告。

        ![插图 1](/api/images/i1)

        正文间隔足够长用来避免插图堆叠判定失败的可读文字填充。
        """.formatted(filler);
    CourseGenerationContext ctx = ctx(0, 3);
    List<Map<String, Object>> images = List.of(
        Map.of("kind", "cover", "publicUrl", "/api/images/cover",
            "size", "2400x1024", "width", 2400, "height", 1024));
    assertThatThrownBy(() -> validators.validateFinal(draft, wechat, ctx, images))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WECHAT_IMAGE_PROMPTS_REMAIN");
  }

  private static String validWechat(String filler) {
    return """
        # 课

        > 系列：AI 测试｜难度：零基础｜预计阅读：5 分钟
        >
        > 本课覆盖：测试
        >
        > 摘要：本课围绕课，讲清测试。

        ![课程封面](/api/images/cover)

        %s

        ## 本课小结

        小结。

        ## 下节课预告

        预告。

        ![插图 1](/api/images/i1)

        正文间隔足够长用来避免插图堆叠判定失败的可读文字填充。
        """.formatted(filler);
  }

  private static CourseGenerationContext ctx(int minImages, int maxImages) {
    return new CourseGenerationContext(
        "c1", "课", "测试", "s1", "测试", 1, "零基础", "4000-6000", "up-to-3",
        8000, minImages, maxImages, "", "", "", "", List.of(), "draft", "wechat");
  }
}
