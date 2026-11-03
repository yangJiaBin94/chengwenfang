package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.application.research.ResearchKeywordPlan;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchItemKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ResearchRelevanceFilter} 全否信任与解析失败 fail-open。 */
@ExtendWith(MockitoExtension.class)
class ResearchRelevanceFilterTest {

  @Mock
  private AiTextFacade ai;

  @InjectMocks
  private ResearchRelevanceFilter filter;

  @Test
  void all_rejected_returns_empty_not_fallback() {
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("""
            {"items":[
              {"id":"0","relevant":false,"reason":"水果"},
              {"id":"1","relevant":false,"reason":"无关"}
            ]}
            """)
        .build());

    List<ResearchCandidate> kept = filter.filterRelevant(
        "苹果公司财报",
        "Apple Inc",
        ResearchKeywordPlan.builder().subject("Apple Inc").disambiguation("不是水果").build(),
        List.of(web("水果百科"), web("香蕉种植")));

    assertThat(kept).isEmpty();
  }

  @Test
  void empty_parse_fails_open_keeps_all() {
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("不是合法 JSON")
        .build());

    List<ResearchCandidate> input = List.of(web("候选甲"), web("候选乙"));
    List<ResearchCandidate> kept = filter.filterRelevant(
        "选题",
        "",
        ResearchKeywordPlan.builder().subject("选题").build(),
        input);

    assertThat(kept).isSameAs(input);
  }

  private static ResearchCandidate web(String title) {
    return ResearchCandidate.builder()
        .title(title)
        .url("https://example.com/" + title)
        .summary(title)
        .kind(ResearchItemKind.WEB)
        .build();
  }
}
