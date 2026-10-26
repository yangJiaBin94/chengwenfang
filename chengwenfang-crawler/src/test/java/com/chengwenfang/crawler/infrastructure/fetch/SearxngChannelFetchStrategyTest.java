package com.chengwenfang.crawler.infrastructure.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.constant.UpstreamJsonFields;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearxngChannelFetchStrategyTest {
  @Test
  void resolveTimeRange_maps_hours_window() {
    assertEquals(UpstreamJsonFields.TIME_RANGE_DAY, SearxngChannelFetchStrategy.resolveTimeRange(24));
    assertEquals(UpstreamJsonFields.TIME_RANGE_WEEK, SearxngChannelFetchStrategy.resolveTimeRange(72));
    assertEquals(UpstreamJsonFields.TIME_RANGE_MONTH, SearxngChannelFetchStrategy.resolveTimeRange(720));
    assertEquals(UpstreamJsonFields.TIME_RANGE_MONTH, SearxngChannelFetchStrategy.resolveTimeRange(null));
    assertEquals(UpstreamJsonFields.TIME_RANGE_MONTH, SearxngChannelFetchStrategy.resolveTimeRange(0));
  }

  @Test
  void sanitizeQueries_drops_long_and_caps_count() {
    List<String> raw = new ArrayList<>();
    raw.add("  血脂轻度升高 生活干预  ");
    raw.add("动脉粥样硬化早期信号");
    raw.add("动脉粥样硬化早期信号");
    String mega = "血管养护、血脂尿酸、睡眠退化、关节保养、更年期 / 男性精力、减脂塑形 "
        + "身体局部 & 脏腑养生, 饮食养生, 运动 & 体态养生, 心理 / 情绪养生, 避坑 & 辟谣向";
    raw.add(mega);
    for (int index = 0; index < 30; index++) {
      raw.add("关键词" + index);
    }

    List<String> sanitized = SearxngChannelFetchStrategy.sanitizeQueries(raw);
    assertTrue(sanitized.contains("血脂轻度升高 生活干预"));
    assertFalse(sanitized.stream().anyMatch(query -> query.length() > ChannelFetchDefaults.SEARXNG_MAX_QUERY_CHARS));
    assertEquals(ChannelFetchDefaults.SEARXNG_MAX_QUERIES_PER_FETCH, sanitized.size());
  }
}
