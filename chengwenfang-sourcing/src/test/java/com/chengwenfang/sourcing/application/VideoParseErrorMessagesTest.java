package com.chengwenfang.sourcing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
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

  @Test
  void humanizes_missing_python_executable() {
    String msg = VideoParseErrorMessages.humanize(
        "Cannot run program \"scripts/video-transcript/.venv/Scripts/python.exe\": CreateProcess error=2, 系统找不到指定的文件。",
        VideoParseStep.DOWNLOAD_AUDIO);
    assertThat(msg).contains("找不到视频解析 Python 环境").contains("下载音频");
  }
}
