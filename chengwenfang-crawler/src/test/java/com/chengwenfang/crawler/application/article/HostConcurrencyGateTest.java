package com.chengwenfang.crawler.application.article;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link HostConcurrencyGate} 主机键解析。 */
class HostConcurrencyGateTest {
  @Test
  void host_key_normalizes_www_and_scheme() {
    assertEquals("example.com", HostConcurrencyGate.hostKey("https://Example.com/a"));
    assertEquals("news.163.com", HostConcurrencyGate.hostKey("http://news.163.com/x"));
    assertEquals("unknown", HostConcurrencyGate.hostKey(""));
  }
}
