package com.chengwenfang.knowledge.rag;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 金标规则断言：应拒答档必须 expectAbstain；证据门常量符合成熟方案。
 */
class RagGoldenRuleAssertTest {

  @Test
  void evidenceGateRequiresAtLeastTwoCitations() {
    assertThat(KnowledgeRagConstants.MIN_CITATIONS_TO_ANSWER).isGreaterThanOrEqualTo(2);
  }

  @Test
  void shouldAbstainTierExpectsAbstain() throws Exception {
    try (InputStream stream = getClass().getClassLoader()
        .getResourceAsStream("rag-golden/should-abstain.json")) {
      assertThat(stream).isNotNull();
      JSONArray array = JSONUtil.parseArray(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      for (Object item : array) {
        JSONObject row = (JSONObject) item;
        assertThat(row.getBool("expectAbstain")).isTrue();
        assertThat(row.getStr("question")).isNotBlank();
      }
    }
  }

  @Test
  void simpleFactTierHasQuestions() throws Exception {
    try (InputStream stream = getClass().getClassLoader()
        .getResourceAsStream("rag-golden/simple-fact.json")) {
      assertThat(stream).isNotNull();
      JSONArray array = JSONUtil.parseArray(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      assertThat(array).isNotEmpty();
      for (Object item : array) {
        JSONObject row = (JSONObject) item;
        assertThat(row.getStr("tier")).isEqualTo("simple_fact");
        assertThat(row.getStr("question")).isNotBlank();
      }
    }
  }
}
