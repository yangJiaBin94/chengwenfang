package com.chengwenfang.crawler.facade;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link PublishDateSnippetSupport} 正则截取。 */
class PublishDateSnippetSupportTest {

  @Test
  void buildsSnippetsAroundDates() {
    String text = "作者马小丽 2024-08-27 64448次 中老年一般可通过合理饮食养生 pubDate 2024-08-23T10:28:54";
    String context = PublishDateSnippetSupport.buildAiContext(text);
    assertTrue(context.contains("【候选"));
    assertTrue(context.contains("2024-08-27"));
  }
}
