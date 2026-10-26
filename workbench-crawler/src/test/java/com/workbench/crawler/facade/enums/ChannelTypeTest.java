package com.workbench.crawler.facade.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** {@link ChannelType} 解析契约。 */
class ChannelTypeTest {

  @Test
  void from_识别已知类型并忽略大小写() {
    assertEquals(ChannelType.RSS, ChannelType.from("rss"));
    assertEquals(ChannelType.SEARXNG, ChannelType.from("SearXNG"));
    assertEquals(ChannelType.TAVILY, ChannelType.from("TAVILY"));
  }

  @Test
  void from_未知或空白返回null() {
    assertNull(ChannelType.from(null));
    assertNull(ChannelType.from(" "));
    assertNull(ChannelType.from("twitter"));
  }
}
