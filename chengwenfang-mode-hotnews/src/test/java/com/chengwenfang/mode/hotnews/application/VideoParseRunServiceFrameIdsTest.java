package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link VideoParseRunService} 抽帧 id / URL 归一化纯逻辑。 */
class VideoParseRunServiceFrameIdsTest {

  @Test
  void parseFrameFileIds_readsJsonArray() {
    assertThat(VideoParseRunService.parseFrameFileIds(null)).isEmpty();
    assertThat(VideoParseRunService.parseFrameFileIds("[]")).isEmpty();
    assertThat(VideoParseRunService.parseFrameFileIds(
            "[\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\"]"))
        .containsExactly(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  }

  @Test
  void parseFrameCaptions_readsJsonArray() {
    assertThat(VideoParseRunService.parseFrameCaptions(null)).isEmpty();
    assertThat(VideoParseRunService.parseFrameCaptions("[]")).isEmpty();
    assertThat(VideoParseRunService.parseFrameCaptions(
            "[\"画面显示涨幅 288%\",\"主播面对镜头\"]"))
        .containsExactly("画面显示涨幅 288%", "主播面对镜头");
  }

  @Test
  void hasUsableCaptions_rejectsBlankOnlyArrays() {
    assertThat(VideoParseRunService.hasUsableCaptions(null)).isFalse();
    assertThat(VideoParseRunService.hasUsableCaptions("[\"\",\"\"]")).isFalse();
    assertThat(VideoParseRunService.hasUsableCaptions("[\"画面有数字\"]")).isTrue();
  }

  @Test
  void alignCaptions_padsAndTruncates() {
    assertThat(VideoParseRunService.alignCaptions(3, List.of("a")))
        .containsExactly("a", "", "");
    assertThat(VideoParseRunService.alignCaptions(2, List.of("a", "b", "c")))
        .containsExactly("a", "b");
  }

  @Test
  void urlAliases_coversHttpHttpsAndTrailingSlash() {
    Set<String> aliases = VideoParseRunService.urlAliases(
        "https://www.bilibili.com/video/av1/");
    assertThat(aliases).contains(
        "https://www.bilibili.com/video/av1",
        "http://www.bilibili.com/video/av1");
  }
}
