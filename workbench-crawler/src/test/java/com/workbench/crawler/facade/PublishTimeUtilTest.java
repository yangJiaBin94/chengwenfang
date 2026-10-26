package com.workbench.crawler.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PublishTimeUtilTest {
  @Test
  void infer_calendar_path_and_arxiv() {
    assertEquals(
        LocalDateTime.of(2026, 7, 11, 0, 0),
        PublishTimeUtil.inferPublishedAtFromUrl(
            "https://unwire.hk/2026/07/11/openai-gpt-5-6-microsoft-copilot/"));
    assertEquals(
        LocalDateTime.of(2025, 10, 1, 0, 0),
        PublishTimeUtil.inferPublishedAtFromUrl("https://arxiv.org/abs/2510.14878"));
    assertNull(PublishTimeUtil.inferPublishedAtFromUrl(
        "https://www.36kr.com/p/3388562868125827"));
  }

  @Test
  void search_keeps_unknown_date_and_drops_only_stale() {
    assertFalse(PublishTimeUtil.isStaleOrUnknownForSearch(null, 72));
    assertFalse(PublishTimeUtil.isStaleOrUnknownForSearch(
        LocalDateTime.now().minusHours(12), 72));
    assertTrue(PublishTimeUtil.isStaleOrUnknownForSearch(
        LocalDateTime.now().minusDays(30), 72));
  }
}
