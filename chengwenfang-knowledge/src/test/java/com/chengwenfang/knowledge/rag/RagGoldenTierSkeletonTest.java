package com.chengwenfang.knowledge.rag;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 金标分档骨架：校验三档 JSON 可加载且字段齐备（Recall 评测在有索引后补 expect）。
 */
class RagGoldenTierSkeletonTest {

  private static final Set<String> TIERS = Set.of("simple_fact", "multi_hop", "should_abstain");

  /**
   * 简单事实档可解析。
   */
  @Test
  void simpleFactTierLoads() {
    assertTierFile("rag-golden/simple-fact.json", "simple_fact");
  }

  /**
   * 多跳档可解析。
   */
  @Test
  void multiHopTierLoads() {
    assertTierFile("rag-golden/multi-hop.json", "multi_hop");
  }

  /**
   * 应拒答档可解析。
   */
  @Test
  void shouldAbstainTierLoads() {
    assertTierFile("rag-golden/should-abstain.json", "should_abstain", true);
  }

  /**
   * 加载并断言档位字段。
   *
   * @param classpath      资源路径
   * @param expectedTier   期望档
   * @param requireAbstain 是否要求 expectAbstain=true
   */
  private static void assertTierFile(
      String classpath, String expectedTier, boolean requireAbstain) {
    try (InputStream stream = RagGoldenTierSkeletonTest.class
        .getClassLoader()
        .getResourceAsStream(classpath)) {
      assertThat(stream).as(classpath).isNotNull();
      String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      JSONArray array = JSONUtil.parseArray(json);
      assertThat(array).isNotEmpty();
      for (Object item : array) {
        JSONObject row = (JSONObject) item;
        assertThat(row.getStr("id")).isNotBlank();
        assertThat(row.getStr("tier")).isEqualTo(expectedTier);
        assertThat(TIERS).contains(row.getStr("tier"));
        assertThat(row.getStr("question")).isNotBlank();
        if (requireAbstain) {
          assertThat(row.getBool("expectAbstain")).isTrue();
        }
      }
    } catch (Exception exception) {
      throw new AssertionError("加载金标失败: " + classpath, exception);
    }
  }

  private static void assertTierFile(String classpath, String expectedTier) {
    assertTierFile(classpath, expectedTier, false);
  }
}
