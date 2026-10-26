package com.chengwenfang.crawler.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link HtmlDateTextSupport} 去标签保留 script 日期。 */
class HtmlDateTextSupportTest {

  @Test
  void keepsScriptPubDateAndStripsStyle() {
    String html = """
        <html><head>
        <style>.a{color:red}</style>
        <script type="application/json">
        {"pubDate":"2024-08-23T10:28:54","upDate":"2024-08-27T17:50:34"}
        </script>
        </head><body><h1>中老年怎么养生</h1><p>正文</p></body></html>
        """;
    String text = HtmlDateTextSupport.toDateInputText(html);
    assertTrue(text.contains("pubDate"));
    assertTrue(text.contains("2024-08-27"));
    assertFalse(text.contains("color:red"));
    assertFalse(text.contains("<h1>"));
  }
}
