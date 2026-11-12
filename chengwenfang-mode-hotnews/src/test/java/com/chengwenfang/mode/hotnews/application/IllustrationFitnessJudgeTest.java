package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link IllustrationFitnessJudge} 快速复用与重生。 */
@ExtendWith(MockitoExtension.class)
class IllustrationFitnessJudgeTest {

  @Mock
  private AiTextFacade ai;

  @Test
  void same_prompt_reuses_image_without_ai() {
    String oldDraft = """
        # 标题

        正文。

        **插图位置：封面图**

        ```text
        封面提示词A
        ```

        ![](/api/images/img-cover)
        """;
    String newDraft = """
        # 标题

        优化后正文。

        **插图位置：封面图**

        ```text
        封面提示词A
        ```
        """;
    IllustrationFitnessJudge judge = new IllustrationFitnessJudge(ai);
    IllustrationFitnessJudge.MergeResult result = judge.merge(oldDraft, newDraft, "建议");
    assertThat(result.getReport().getReusedCount()).isEqualTo(1);
    assertThat(result.getReport().getRegenRequiredCount()).isZero();
    assertThat(result.getDraft()).contains("![](/api/images/img-cover)");
    verify(ai, never()).completeText(any(TextCompleteRequest.class));
  }

  @Test
  void reordered_slots_match_by_prompt_not_sequence() {
    String oldDraft = """
        **插图位置：插图 1｜A**

        ```text
        提示词甲
        ```

        ![](/api/images/img-a)

        **插图位置：插图 2｜B**

        ```text
        提示词乙
        ```

        ![](/api/images/img-b)
        """;
    // 新稿把「乙」放到第一张，序号变了但提示词相同，应挂回 img-b
    String newDraft = """
        **插图位置：插图 1｜B**

        ```text
        提示词乙
        ```
        """;
    IllustrationFitnessJudge judge = new IllustrationFitnessJudge(ai);
    IllustrationFitnessJudge.MergeResult result = judge.merge(oldDraft, newDraft, "调整顺序");
    assertThat(result.getReport().getReusedCount()).isEqualTo(1);
    assertThat(result.getDraft()).contains("![](/api/images/img-b)");
    assertThat(result.getDraft()).doesNotContain("![](/api/images/img-a)");
    verify(ai, never()).completeText(any(TextCompleteRequest.class));
  }

  @Test
  void changed_prompt_asks_ai_and_can_regenerate() {
    String oldDraft = """
        **插图位置：封面图**

        ```text
        旧封面
        ```

        ![](/api/images/img-cover)
        """;
    String newDraft = """
        **插图位置：封面图**

        ```text
        新封面冲突画面
        ```

        ![](/api/images/img-cover)
        """;
    when(ai.completeText(any(TextCompleteRequest.class)))
        .thenReturn(TextCompleteResult.builder()
            .text("""
                {"slots":[{"key":"cover","decision":"regenerate","reason":"封面冲突变了"}]}
                """)
            .build());
    IllustrationFitnessJudge judge = new IllustrationFitnessJudge(ai);
    IllustrationFitnessJudge.MergeResult result = judge.merge(oldDraft, newDraft, "换封面");
    assertThat(result.getReport().getRegenRequiredCount()).isEqualTo(1);
    assertThat(result.getDraft()).doesNotContain("![](/api/images/img-cover)");
    assertThat(result.getDraft()).contains("新封面冲突画面");
  }
}
