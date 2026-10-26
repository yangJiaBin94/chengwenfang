package com.workbench.quality.facade.dto.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 审核引擎结果反序列化：兼容 View / Map 混入。
 */
class HotArticleReviewEngineResultTest {

  @Test
  void fromMap_acceptsHotArticleRuleResultViewRows() {
    HotArticleRuleResultView view = HotArticleRuleResultView.builder()
        .code("ARTICLE_TITLE_REQUIRED")
        .status("passed")
        .blocking(true)
        .detail("一级标题存在")
        .title("标题")
        .build();
    HotArticleReviewEngineResult result = HotArticleReviewEngineResult.fromMap(Map.of(
        "status", "passed",
        "blockingFailed", false,
        "results", List.of(view)));

    assertThat(result.getResults()).hasSize(1);
    assertThat(result.getResults().get(0).get("code")).isEqualTo("ARTICLE_TITLE_REQUIRED");
    assertThat(result.getResults().get(0).get("status")).isEqualTo("passed");
  }
}
