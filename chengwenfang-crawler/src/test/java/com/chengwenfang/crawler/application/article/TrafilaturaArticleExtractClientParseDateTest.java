package com.chengwenfang.crawler.application.article;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link TrafilaturaArticleExtractClient#parseDate(String)} 格式兼容。 */
class TrafilaturaArticleExtractClientParseDateTest {

  @Test
  void parsesIsoSlashAndChinese() {
    assertEquals(
        LocalDateTime.of(2024, 8, 27, 0, 0),
        TrafilaturaArticleExtractClient.parseDate("2024-08-27"));
    assertEquals(
        LocalDateTime.of(2024, 8, 27, 0, 0),
        TrafilaturaArticleExtractClient.parseDate("2024/08/27"));
    assertEquals(
        LocalDateTime.of(2024, 8, 27, 0, 0),
        TrafilaturaArticleExtractClient.parseDate("2024年8月27日"));
    assertEquals(
        LocalDateTime.of(2024, 8, 27, 17, 50, 34),
        TrafilaturaArticleExtractClient.parseDate("2024-08-27T17:50:34"));
    assertNotNull(TrafilaturaArticleExtractClient.parseDate("\"2024-08-27\""));
    assertNull(TrafilaturaArticleExtractClient.parseDate(null));
    assertNull(TrafilaturaArticleExtractClient.parseDate("not-a-date"));
  }
}
