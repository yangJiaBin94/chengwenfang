package com.workbench.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 搜索词规范化单测。 */
class SearchQueryNormalizerTest {

  @Test
  void splits_pasted_outline_into_short_atoms() {
    String mega =
        "血管养护、血脂尿酸、睡眠退化、关节保养、更年期 / 男性精力、减重难、体检报告解读、防止过度进补。"
            + "骨质疏松、防跌倒、肠道健康、合理补钙、中成药 / 补品避雷、低盐饮食、认知保养（预防记忆力衰退）。"
            + "身体局部 & 脏腑养生，饮食养生，运动 & 体态养生，心理 / 情绪养生，避坑 & 辟谣向养生血管养护 中年人";
    List<String> normalized = SearchQueryNormalizer.normalize(List.of(mega, "血脂轻度升高 生活干预"));
    assertTrue(normalized.contains("血脂轻度升高 生活干预"));
    assertTrue(normalized.contains("血管养护"));
    assertTrue(normalized.contains("血脂尿酸"));
    assertFalse(normalized.stream().anyMatch(query -> query.length() > SearchQueryNormalizer.MAX_QUERY_CHARS));
    assertTrue(normalized.size() <= SearchQueryNormalizer.MAX_QUERIES);
  }

  @Test
  void caps_query_count() {
    List<String> raw = new java.util.ArrayList<>();
    for (int index = 0; index < 30; index++) {
      raw.add("关键词" + index);
    }
    assertEquals(SearchQueryNormalizer.MAX_QUERIES, SearchQueryNormalizer.normalize(raw).size());
  }
}
