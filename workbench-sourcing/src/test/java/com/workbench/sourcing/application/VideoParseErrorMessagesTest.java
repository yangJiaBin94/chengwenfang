package com.workbench.sourcing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.sourcing.facade.enums.VideoParseStep;
import org.junit.jupiter.api.Test;

class VideoParseErrorMessagesTest {
  @Test
  void humanizes_gbk_codec_error() {
    String msg = VideoParseErrorMessages.humanize(
        "'gbk' codec can't encode character '\\u203c' in position 39: illegal multibyte sequence",
        VideoParseStep.ASR);
    assertThat(msg).contains("特殊字符").contains("语音识别").doesNotContain("gbk");
  }

  @Test
  void humanizes_missing_whisper() {
    String msg = VideoParseErrorMessages.humanize(
        "未安装 openai-whisper: No module named 'whisper'",
        VideoParseStep.ASR);
    assertThat(msg).contains("Whisper").doesNotContain("No module");
  }
}
