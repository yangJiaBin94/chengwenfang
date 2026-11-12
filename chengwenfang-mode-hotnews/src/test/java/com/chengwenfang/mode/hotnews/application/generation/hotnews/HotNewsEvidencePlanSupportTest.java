package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.application.generation.hotnews.model.HotNewsAttachEvidencePlan;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsEvidenceKeys;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link HotNewsEvidencePlanSupport} 解析与白名单校验。 */
class HotNewsEvidencePlanSupportTest {

  @Test
  void parseAndSanitize_keepsAllowedUrlsAndCapsShots() {
    String raw = """
        {
          "shots": [
            {"url":"https://a.example/x","focusHint":"增速12%","caption":"财联社","insertAfter":"来源：财联社"},
            {"url":"https://evil.example/y","focusHint":"hack","caption":"假"},
            {"url":"https://b.example/y","focusHint":"监管","caption":"央行"},
            {"url":"https://c.example/z","focusHint":"三","caption":"三"},
            {"url":"https://d.example/w","focusHint":"四","caption":"四"}
          ]
        }
        """;
    HotNewsAttachEvidencePlan plan = HotNewsEvidencePlanSupport.parseAndSanitize(
        raw,
        List.of("https://a.example/x", "https://b.example/y", "https://c.example/z", "https://d.example/w"));
    assertThat(plan.getShots()).hasSize(HotNewsEvidenceKeys.MAX_SHOTS);
    assertThat(plan.getShots())
        .extracting(shot -> shot.getUrl())
        .containsExactly("https://a.example/x", "https://b.example/y", "https://c.example/z");
  }

  @Test
  void parseAndSanitize_emptyWhenNoAllowedUrls() {
    HotNewsAttachEvidencePlan plan = HotNewsEvidencePlanSupport.parseAndSanitize(
        "{\"shots\":[{\"url\":\"https://a.example\",\"caption\":\"x\"}]}",
        List.of());
    assertThat(plan.getShots()).isEmpty();
  }

  @Test
  void parseAndSanitize_emptyShotsWhenModelSaysNone() {
    HotNewsAttachEvidencePlan plan = HotNewsEvidencePlanSupport.parseAndSanitize(
        "{\"shots\":[]}",
        List.of("https://a.example"));
    assertThat(plan.getShots()).isEmpty();
  }

  @Test
  void extractAllowedUrls_fromReferenceArray() {
    String json = """
        [{"title":"t","url":"https://news.example/1","summary":"s"},
         {"title":"u","url":"","summary":""}]
        """;
    assertThat(HotNewsEvidencePlanSupport.extractAllowedUrls(json))
        .containsExactly("https://news.example/1");
  }

  @Test
  void parseAndSanitize_stripsCaptionSourcePrefix() {
    String raw = """
        {
          "shots": [
            {"url":"https://a.example/x","focusHint":"150.58","caption":"来源：财联社《肉签》","insertAfter":"150.58元。"}
          ]
        }
        """;
    HotNewsAttachEvidencePlan plan = HotNewsEvidencePlanSupport.parseAndSanitize(
        raw, List.of("https://a.example/x"));
    assertThat(plan.getShots()).hasSize(1);
    assertThat(plan.getShots().get(0).getCaption()).isEqualTo("财联社《肉签》");
  }
}
