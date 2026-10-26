package com.workbench.mode.course.application.course;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workbench.quality.application.AuditFacadeImpl;
import com.workbench.quality.domain.engine.CourseAuditEngine;
import com.workbench.quality.domain.engine.HotArticleReviewEngine;
import com.workbench.quality.domain.engine.KnowledgeRulesEngine;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseGenerationValidatorsStructureTest {
  private final CourseGenerationValidators validators =
      new CourseGenerationValidators(new AuditFacadeImpl(
          new CourseAuditEngine(),
          new KnowledgeRulesEngine(),
          new HotArticleReviewEngine(),
          null,
          null));

  @Test
  void validate_draft_allows_duplicate_summary_like_python() {
    String filler = "开场内容要足够长，用来满足八百字门槛。".repeat(40);
    String draft = "# 课\n\n" + filler
        + "\n\n## 本课小结\n\n第一次。\n\n## 本课小结\n\n第二次。\n\n## 下节课预告\n\n预告。\n";
    // 原稿阶段对齐原版：不因唯一性失败
    assertThatCode(() -> validators.validateDraft(draft, 8000)).doesNotThrowAnyException();
  }

  @Test
  void validate_draft_allows_summary_words_inside_code_fence() {
    String filler = "开场内容要足够长，用来满足八百字门槛。".repeat(40);
    String draft = "# 课\n\n" + filler
        + "\n\n```text\n不要提前写本课小结和下节课预告\n```\n\n"
        + "## 本课小结\n\n小结。\n\n## 下节课预告\n\n预告。\n";
    assertThatCode(() -> validators.validateDraft(draft, 8000)).doesNotThrowAnyException();
  }

  @Test
  void validate_final_ignores_summary_words_inside_code_fence() {
    String filler = "开场内容要足够长，用来满足八百字门槛。".repeat(40);
    String draft = "# 课\n\n" + filler
        + "\n\n```text\n不要提前写本课小结和下节课预告\n```\n\n"
        + "## 本课小结\n\n小结。\n\n## 下节课预告\n\n预告。\n";
    String wechat = """
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
    CourseGenerationContext ctx = new CourseGenerationContext(
        "c1", "课", "测试", "s1", "测试", 1, "零基础", "4000-6000", "up-to-3",
        8000, 0, 3, "", "", "", "", List.of(), "draft", "wechat");
    List<java.util.Map<String, Object>> images = List.of(
        java.util.Map.of(
            "kind", "cover",
            "publicUrl", "/api/images/cover",
            "size", "2400x1024",
            "width", 2400,
            "height", 1024));
    assertThatCode(() -> validators.validateFinal(draft, wechat, ctx, images))
        .doesNotThrowAnyException();
  }

  @Test
  void validate_draft_rejects_material_requests() {
    String draft = "请提供完整课程素材。请提供课程素材。" + "正文字样。".repeat(200)
        + "\n\n## 本课小结\n\n小结\n\n## 下节课预告\n\n预告\n";
    assertThatThrownBy(() -> validators.validateDraft(draft, 8000))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GENERATED_CONTENT_REQUESTED_MATERIALS");
  }
}
