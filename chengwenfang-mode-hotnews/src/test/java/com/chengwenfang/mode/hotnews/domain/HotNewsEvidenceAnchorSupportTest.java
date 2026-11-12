package com.chengwenfang.mode.hotnews.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link HotNewsEvidenceAnchorSupport}：从正文中选出可定位短锚点。 */
class HotNewsEvidenceAnchorSupportTest {

  @Test
  void looksLikePdfUrl_detectsPdfPath() {
    assertThat(HotNewsEvidenceAnchorSupport.looksLikePdfUrl(
            "http://disc.static.szse.cn/a/b.PDF")).isTrue();
    assertThat(HotNewsEvidenceAnchorSupport.looksLikePdfUrl(
            "https://m.cls.cn/detail/2523435")).isFalse();
  }

  @Test
  void resolveFindText_picksSentenceMatchingHintFragment() {
    String body = """
        公司提示投资者理性投资，勿盲目炒新。
        按盘中最高价150.58元计算，中一签最多赚6.6万元。
        收盘报84.55元，涨397.94%。
        """;
    String hint = "中一签最多赚6.6万元；盘中最高价150.58元";
    String anchor = HotNewsEvidenceAnchorSupport.resolveFindText(body, hint);
    assertThat(anchor).isNotBlank();
    assertThat(anchor.length()).isLessThanOrEqualTo(40);
    assertThat(body).contains(anchor);
    assertThat(anchor).containsAnyOf("6.6万", "150.58");
  }

  @Test
  void resolveFindText_handlesCurlyQuotesAndSplitClaims() {
    String body = "公告提醒投资者理性投资，勿盲目\"炒新\"，并披露归母净利润26,363.88万元至27,826.11万元。";
    String hint = "理性投资，勿盲目“炒新”；归母净利润26,363.88万元至27,826.11万元";
    String anchor = HotNewsEvidenceAnchorSupport.resolveFindText(body, hint);
    assertThat(anchor).isNotBlank();
    assertThat(anchor).containsAnyOf("26,363.88", "炒新", "理性投资");
  }

  @Test
  void resolveFindText_returnsNullWhenNoOverlap() {
    String body = "今天天气不错，适合出去走走。";
    String hint = "中一签最多赚6.6万元";
    assertThat(HotNewsEvidenceAnchorSupport.resolveFindText(body, hint)).isNull();
  }
}
