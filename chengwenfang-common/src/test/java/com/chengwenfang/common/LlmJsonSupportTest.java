package com.chengwenfang.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.Data;
import org.junit.jupiter.api.Test;

/** {@link LlmJsonSupport} 从模型文本截取 JSON。 */
class LlmJsonSupportTest {

  @Test
  void parsesWrappedJson() {
    String text = "说明如下：\n{\"publishedAt\":\"2024-08-27\",\"reason\":\"文头\"}\n结束";
    Sample sample = LlmJsonSupport.parseObject(text, Sample.class);
    assertNotNull(sample);
    assertEquals("2024-08-27", sample.getPublishedAt());
    assertTrue(sample.getReason().contains("文头"));
    assertNull(LlmJsonSupport.parseObject("no json", Sample.class));
  }

  @Data
  public static class Sample {
    private String publishedAt;
    private String reason;
  }
}
