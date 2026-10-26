package com.chengwenfang.crawler.facade.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link ArticleContentSource}。 */
class ArticleContentSourceTest {
  @Test
  void from_and_matches() {
    assertEquals(ArticleContentSource.JINA, ArticleContentSource.from("jina"));
    assertEquals(ArticleContentSource.LIGHTPANDA, ArticleContentSource.from("lightpanda"));
    assertEquals(ArticleContentSource.NONE, ArticleContentSource.from(""));
    assertTrue(ArticleContentSource.SUMMARY.matches("summary"));
    assertTrue(ArticleContentSource.TRAFILATURA.isResolved());
    assertTrue(ArticleContentSource.LIGHTPANDA.isResolved());
  }
}
