package com.workbench.mode.hotnews.application;

import com.workbench.mode.hotnews.facade.VideoParseSink;
import com.workbench.mode.hotnews.facade.VideoTranscriptFacade;
import com.workbench.sourcing.facade.enums.VideoParseStep;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link VideoTranscriptFacade} 默认实现：委托 {@link VideoTranscriptService}。
 */
@Service
@RequiredArgsConstructor
public class VideoTranscriptFacadeImpl implements VideoTranscriptFacade {
  /** 视频转录领域服务。 */
  private final VideoTranscriptService videoTranscript;

  @Override
  public Double probeDurationSeconds(String url) {
    return videoTranscript.probeDurationSeconds(url);
  }

  @Override
  public String durationLimitRejectReason(String url) {
    return videoTranscript.durationLimitRejectReason(url);
  }

  @Override
  public void parseSink(
      VideoParseSink sink, VideoParseStep fromStep, BiConsumer<String, String> progressHook) {
    videoTranscript.parseSink(sink, fromStep, progressHook);
  }

  @Override
  public int cleanupExpiredWorkDirs() {
    return videoTranscript.cleanupExpiredWorkDirs();
  }
}
