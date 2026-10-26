package com.workbench.sourcing.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 口播文本质量门单测。 */
class VideoSpeechTextQualityTest {

  @Test
  void acceptsNormalChineseSpeech() {
    String text = "Reflection AI 今天发布了 Beam 模型，总参数约 501B，激活约 23B，"
        + "在多个公开基准上接近领先闭源模型。本节还会对比训练数据与推理成本。";
    assertTrue(VideoSpeechTextQuality.isValidSpeechText(text));
  }

  @Test
  void rejectsHeheHallucination() {
    assertFalse(VideoSpeechTextQuality.isValidSpeechText("嘿嘿!!!!!!!!嘿嘿"));
    assertFalse(VideoSpeechTextQuality.isValidSpeechText("嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿嘿"));
  }

  @Test
  void rejectsTooShort() {
    assertFalse(VideoSpeechTextQuality.isValidSpeechText("今天发布了新模型"));
  }
}
