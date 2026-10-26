package com.chengwenfang.publishing.domain.styling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 主题 HTML 内联样式与相对图片路径保留。 */
class ThemeHtmlStylerUtilTest {

  @Test
  void styleKeepsRelativeApiImageSrc() {
    String styled = ThemeHtmlStylerUtil.style(
        "<p>正文</p><img src=\"/api/images/fe885881-66d6-4845-adda-e70d4370c42f\" alt=\"\">",
        Map.of());
    assertThat(styled).contains("src=\"/api/images/fe885881-66d6-4845-adda-e70d4370c42f\"");
    assertThat(styled).doesNotContain("chengwenfang.rel.invalid");
  }

  @Test
  void protectAndRestoreRoundTripRelativeImageSrc() {
    String raw = "<img src=\"/api/images/abc\" alt=\"x\">";
    String protectedHtml = ThemeHtmlStylerUtil.protectRelativeImageSrc(raw);
    assertThat(protectedHtml).contains("https://chengwenfang.rel.invalid/api/images/abc");
    assertThat(ThemeHtmlStylerUtil.restoreRelativeImageSrc(protectedHtml)).isEqualTo(raw);
  }
}
