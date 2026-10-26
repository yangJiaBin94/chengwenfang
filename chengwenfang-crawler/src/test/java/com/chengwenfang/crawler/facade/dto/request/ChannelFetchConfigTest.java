package com.chengwenfang.crawler.facade.dto.request;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ChannelFetchConfig} 查询词与网页多地址解析。 */
class ChannelFetchConfigTest {

  @Test
  void resolveQueries_优先列表否则单条() {
    ChannelFetchConfig withList = ChannelFetchConfig.builder()
        .queries(List.of(" a ", "", "b"))
        .query("ignored")
        .build();
    assertEquals(List.of("a", "b"), withList.resolveQueries());

    ChannelFetchConfig single = ChannelFetchConfig.builder().query("solo").build();
    assertEquals(List.of("solo"), single.resolveQueries());
  }

  @Test
  void resolveUrls_去空白并保留顺序() {
    ChannelFetchConfig config = ChannelFetchConfig.builder()
        .urls(List.of(" https://a.example/ ", "", "https://b.example/"))
        .build();
    assertEquals(List.of("https://a.example/", "https://b.example/"), config.resolveUrls());

    ChannelFetchConfig empty = new ChannelFetchConfig();
    assertEquals(List.of(), empty.resolveUrls());
  }
}
