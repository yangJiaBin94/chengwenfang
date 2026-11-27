package com.chengwenfang.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link LoginIpRegionLookup} 本机/内网分支。 */
class LoginIpRegionLookupTest {

  @Test
  void resolveCity_localhost_returns本机() {
    assertEquals("本机", LoginIpRegionLookup.resolveCity("127.0.0.1"));
    assertEquals("本机", LoginIpRegionLookup.resolveCity("::1"));
  }

  @Test
  void resolveCity_privateIpv4_returns内网() {
    assertEquals("内网", LoginIpRegionLookup.resolveCity("192.168.1.8"));
    assertEquals("内网", LoginIpRegionLookup.resolveCity("10.0.0.2"));
  }

  @Test
  void resolveCity_blank_returnsEmpty() {
    assertEquals("", LoginIpRegionLookup.resolveCity(""));
    assertEquals("", LoginIpRegionLookup.resolveCity(null));
  }
}
