package com.workbench.mode.hotnews.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workbench.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 成文参考过滤：未解析视频跳过，解析成功可纳入。 */
class HotNewsTopicResearchAssistServiceFilterTest {

  @Test
  void filterSkipsUnparsedVideoKeepsParsed() {
    HotNewsResearchItemView article = HotNewsResearchItemView.builder()
        .id("a1")
        .title("文章稿")
        .url("https://example.com/post")
        .summary("文章摘要")
        .mediaLabel("文章")
        .build();
    HotNewsResearchItemView videoPending = HotNewsResearchItemView.builder()
        .id("v1")
        .title("未解析视频")
        .url("https://www.bilibili.com/video/BV1xx411c7mD")
        .summary("搜索摘要")
        .mediaLabel("视频")
        .videoParseStatus("none")
        .build();
    HotNewsResearchItemView videoOk = HotNewsResearchItemView.builder()
        .id("v2")
        .title("已解析视频")
        .url("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        .summary("AI 总结要点")
        .mediaLabel("视频")
        .videoParseStatus("succeeded")
        .build();

    List<HotNewsResearchItemView> refs =
        HotNewsTopicResearchAssistService.filterArticleReferences(
            List.of(article, videoPending, videoOk));

    assertEquals(2, refs.size());
    assertTrue(refs.stream().anyMatch(item -> "a1".equals(item.getId())));
    assertTrue(refs.stream().anyMatch(item -> "v2".equals(item.getId())));
    assertTrue(refs.stream().noneMatch(item -> "v1".equals(item.getId())));
  }
}
