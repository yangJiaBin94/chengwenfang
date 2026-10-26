package com.chengwenfang.crawler.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link PublishTimeUtil#parseFlexibleDate(String)} 格式兼容。 */
class PublishTimeUtilParseFlexibleDateTest {

  @Test
  void parsesIsoSlashAndChinese() {
    assertEquals(
        LocalDateTime.of(2024, 8, 27, 0, 0),
        PublishTimeUtil.parseFlexibleDate("2024-08-27"));
    assertEquals(
        LocalDateTime.of(2024, 8, 27, 0, 0),
        PublishTimeUtil.parseFlexibleDate("2024/08/27"));
    assertEquals(
        LocalDateTime.of(2024, 8, 27, 0, 0),
        PublishTimeUtil.parseFlexibleDate("2024年8月27日"));
    assertEquals(
        LocalDateTime.of(2024, 8, 27, 17, 50, 34),
        PublishTimeUtil.parseFlexibleDate("2024-08-27T17:50:34"));
    assertNotNull(PublishTimeUtil.parseFlexibleDate("\"2024-08-27\""));
    assertNull(PublishTimeUtil.parseFlexibleDate(null));
    assertNull(PublishTimeUtil.parseFlexibleDate("not-a-date"));
  }
}
