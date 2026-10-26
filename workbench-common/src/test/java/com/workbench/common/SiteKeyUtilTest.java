package com.workbench.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link SiteKeyUtil} 单测。 */
class SiteKeyUtilTest {
  @Test
  void extractsRegisteredDomainStyleHost() {
    assertEquals("example.com", SiteKeyUtil.fromUrl("https://www.example.com/path?q=1"));
    assertEquals("news.example.com", SiteKeyUtil.fromUrl("http://news.example.com/a"));
  }

  @Test
  void normalizesBilibiliAndYoutubeHosts() {
    assertEquals("bilibili.com", SiteKeyUtil.fromUrl("https://www.bilibili.com/video/BV1xx"));
    assertEquals("bilibili.com", SiteKeyUtil.fromUrl("https://m.bilibili.com/video/BV1xx"));
    assertEquals("bilibili.com", SiteKeyUtil.fromUrl("https://b23.tv/abcd"));
    assertEquals("youtube.com", SiteKeyUtil.fromUrl("https://www.youtube.com/watch?v=abc"));
    assertEquals("youtube.com", SiteKeyUtil.fromUrl("https://youtu.be/abc"));
  }

  @Test
  void blankOrInvalidReturnsEmpty() {
    assertEquals("", SiteKeyUtil.fromUrl(null));
    assertEquals("", SiteKeyUtil.fromUrl(" "));
    assertEquals("", SiteKeyUtil.fromUrl("not a url :::"));
  }
}
