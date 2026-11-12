package com.chengwenfang.content.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link MarkdownImageRefs} 抽取顺序与去重。 */
class MarkdownImageRefsTest {

  @Test
  void imageIdsInOrder_keepsFirstOccurrenceOrder() {
    String markdown =
        """
        封面 ![](/api/images/cover-a)
        正文 ![](/api/images/inline-1)
        再引封面 ![](/api/images/cover-a)
        尾图 ![](/api/images/inline-2)
        """;
    assertEquals(List.of("cover-a", "inline-1", "inline-2"), MarkdownImageRefs.imageIdsInOrder(markdown));
  }

  @Test
  void imageIdsInOrder_blankReturnsEmpty() {
    assertEquals(List.of(), MarkdownImageRefs.imageIdsInOrder(null));
    assertEquals(List.of(), MarkdownImageRefs.imageIdsInOrder("  "));
    assertEquals(List.of(), MarkdownImageRefs.imageIdsInOrder("无图正文"));
  }
}
