package com.chengwenfang.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link JacksonJsonSupport} 编解码与失败回退。 */
class JacksonJsonSupportTest {

  private final JacksonJsonSupport json = new JacksonJsonSupport(new ObjectMapper());

  @Test
  void writeAndReadRoundTrip() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("a", 1);
    body.put("b", "x");
    String text = json.writeStrict(body);
    assertEquals(body, json.readMapOrEmpty(text));
  }

  @Test
  void readMapOrRawKeepsBrokenPayload() {
    Map<String, Object> map = json.readMapOrRaw("{not-json");
    assertEquals("{not-json", map.get("raw"));
  }

  @Test
  void blankBecomesNullOrEmpty() {
    assertNull(json.readMapOrRaw(""));
    assertTrue(json.readMapOrEmpty("").isEmpty());
    assertEquals(List.of(), json.readMapListOrEmpty(""));
  }
}
