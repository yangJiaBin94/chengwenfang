package com.chengwenfang.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.sourcing.facade.constant.SourceChannelCatalogTags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link HotNewsThemeCatalogTagInferrer} 关键词推断。 */
class HotNewsThemeCatalogTagInferrerTest {

  @Test
  void inferTags_matchesAiFromIntentAndName() {
    Map<String, Object> briefing = baseBriefing("Claude 动态", "盯大模型发布", "抓 AI 产品更新");
    assertThat(HotNewsThemeCatalogTagInferrer.inferTags(briefing))
        .contains(SourceChannelCatalogTags.AI)
        .doesNotContain(SourceChannelCatalogTags.WEB3);
  }

  @Test
  void inferTags_matchesWeb3() {
    Map<String, Object> briefing = baseBriefing("链上热点", "关注 Defi", "追踪 Web3 融资");
    assertThat(HotNewsThemeCatalogTagInferrer.inferTags(briefing))
        .contains(SourceChannelCatalogTags.WEB3);
  }

  @Test
  void inferTags_returnsEmptyWhenUnrelated() {
    Map<String, Object> briefing = baseBriefing("消费品牌", "盯新品上市", "找可写消费案例");
    assertThat(HotNewsThemeCatalogTagInferrer.inferTags(briefing)).isEmpty();
  }

  @Test
  void inferTags_ignoresAsciiSubstringFalsePositives() {
    Map<String, Object> briefing =
        baseBriefing("Daily retail", "method management", "email ethan daily");
    // eth∈ethan、ai∈daily/email，词边界下不应命中
    assertThat(HotNewsThemeCatalogTagInferrer.inferTags(briefing)).isEmpty();
  }

  @Test
  void inferTags_matchesChatGptBrand() {
    // 仅 ChatGPT 复合词，不含 openai/gpt 独立词
    Map<String, Object> briefing = baseBriefing("ChatGPT 观察", "产品动态", "跟进 ChatGPT 更新");
    assertThat(HotNewsThemeCatalogTagInferrer.inferTags(briefing))
        .contains(SourceChannelCatalogTags.AI);
  }

  private static Map<String, Object> baseBriefing(String name, String description, String intent) {
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", name);
    naming.put("description", description);
    naming.put("domain", "custom");
    Map<String, Object> coreIntent = new LinkedHashMap<>();
    coreIntent.put("summary", intent);
    coreIntent.put("material_wants", List.of());
    coreIntent.put("confirmed", true);
    Map<String, Object> briefing = new LinkedHashMap<>();
    briefing.put("naming", naming);
    briefing.put("core_intent", coreIntent);
    return briefing;
  }
}
