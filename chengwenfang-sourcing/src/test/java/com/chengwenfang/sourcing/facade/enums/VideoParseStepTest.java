package com.chengwenfang.sourcing.facade.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 视频解析步骤枚举单测。 */
class VideoParseStepTest {

  @Test
  void cliStepsExcludeJavaSummarize() {
    assertTrue(VideoParseStep.ASR.isCliStep());
    assertTrue(VideoParseStep.EXTRACT_FRAMES.isCliStep());
    assertFalse(VideoParseStep.SUMMARIZE.isCliStep());
    assertFalse(VideoParseStep.VISION_SUMMARIZE.isCliStep());
  }

  @Test
  void visualPipelineOrder() {
    List<VideoParseStep> pipeline = VideoParseStep.visualPipeline();
    assertEquals(
        List.of(
            VideoParseStep.DOWNLOAD_VIDEO,
            VideoParseStep.EXTRACT_FRAMES,
            VideoParseStep.VISION_SUMMARIZE),
        pipeline);
  }

  @Test
  void visualPipelineFromSkipsEarlierSteps() {
    List<VideoParseStep> pipeline =
        VideoParseStep.visualPipelineFrom(VideoParseStep.EXTRACT_FRAMES);
    assertEquals(
        List.of(VideoParseStep.EXTRACT_FRAMES, VideoParseStep.VISION_SUMMARIZE), pipeline);
  }

  @Test
  void videoContentKindRecognizesVisual() {
    assertTrue(VideoContentKind.isVisual("visual"));
    assertFalse(VideoContentKind.isVisual(null));
    assertFalse(VideoContentKind.isVisual("speech"));
  }
}
