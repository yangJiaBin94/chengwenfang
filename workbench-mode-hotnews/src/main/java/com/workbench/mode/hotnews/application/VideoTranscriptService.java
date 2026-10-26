package com.workbench.mode.hotnews.application;

import com.workbench.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.workbench.mode.hotnews.facade.VideoParseSink;
import com.workbench.sourcing.domain.entity.SourceItemEntity;
import com.workbench.sourcing.facade.enums.VideoParseStatus;
import com.workbench.sourcing.facade.enums.VideoParseStep;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资料池视频解析：字幕/ASR 优先，失败则打标 visual 并走抽帧 + 多模态总结。
 *
 * <p>薄门面：对外保持原公开方法签名，内部委派流水线/摘要/落库/时长缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTranscriptService {
  /** 资料池条目解析成功后按摘要重打分。 */
  private final TopicResearchItemScoreService researchItemScoreService;
  /** 解析步骤编排。 */
  private final VideoTranscriptPipeline pipeline;
  /** 资料池/信源 Sink 与运行态落库。 */
  private final VideoTranscriptStore store;
  /** 时长缓存与探测。 */
  private final VideoDurationCache durationCache;

  /**
   * 解析能力是否开启。
   *
   * @return 已启用时为 true
   */
  public boolean isEnabled() {
    return durationCache.isEnabled();
  }

  /**
   * 清理 {@code runtimeDir/video-transcript} 下超过保留天数的条目工作目录。
   *
   * @return 删除的子目录个数
   */
  public int cleanupExpiredWorkDirs() {
    return pipeline.cleanupExpiredWorkDirs();
  }

  /**
   * 清理指定根目录下超期子目录（供单测）。
   *
   * @param root       video-transcript 根目录
   * @param retainDays 保留天数
   * @return 删除的子目录个数
   */
  int cleanupExpiredWorkDirs(Path root, int retainDays) {
    return pipeline.cleanupExpiredWorkDirs(root, retainDays);
  }

  /**
   * 配置的最大允许时长（分钟）；{@code <= 0} 表示不限制。
   *
   * @return 分钟数
   */
  public int maxDurationMinutes() {
    return durationCache.maxDurationMinutes();
  }

  /**
   * 若 URL 对应视频超过时长上限，返回弃用/失败原因；未超限、未配置上限或探测失败时返回 {@code null}。
   *
   * @param url 视频页 URL
   * @return 超限说明；可继续解析时为 null
   */
  public String durationLimitRejectReason(String url) {
    return durationCache.durationLimitRejectReason(url);
  }

  /**
   * 仅读进程内时长缓存，不触发 yt-dlp。
   *
   * @param url 视频页 URL
   * @return 缓存秒数；未探测过为 null
   */
  public Double peekCachedDurationSeconds(String url) {
    return durationCache.peekCachedDurationSeconds(url);
  }

  /**
   * 解析可展示的整数秒：优先缓存，否则探测；失败返回 null。
   *
   * @param url 视频页 URL
   * @return 秒数；未知为 null
   */
  public Integer resolveDurationSeconds(String url) {
    return durationCache.resolveDurationSeconds(url);
  }

  /**
   * 将探测到的秒数取整为可落库整数。
   *
   * @param seconds 探测秒数
   * @return 正整数秒；无效为 null
   */
  public static Integer toRoundedDurationSeconds(Double seconds) {
    return VideoDurationCache.toRoundedDurationSeconds(seconds);
  }

  /**
   * 解析资料池视频条目。
   *
   * @param item         资料条目（须含 url）
   * @param fromStep     起始步骤；null 表示从头或根据已有产物推断
   * @param progressHook 进度回调 (stepKey, message)
   */
  public void parseItem(
      TopicResearchItemEntity item,
      VideoParseStep fromStep,
      BiConsumer<String, String> progressHook) {
    parseSink(store.researchSink(item), fromStep, progressHook);
    // 解析成功后摘要更完整，按新摘要重打综合分
    if (VideoParseStatus.from(item.getVideoParseStatus()).isSucceeded()) {
      try {
        researchItemScoreService.rescorePersistedItem(item);
      } catch (Exception exception) {
        log.warn("视频解析后重打分失败 itemId={} err={}", item.getId(), exception.getMessage());
      }
    }
  }

  /**
   * 解析雷达信源视频条目。
   *
   * @param item         信源条目（须含 url）
   * @param fromStep     起始步骤
   * @param progressHook 进度回调
   */
  public void parseSourceItem(
      SourceItemEntity item,
      VideoParseStep fromStep,
      BiConsumer<String, String> progressHook) {
    parseSink(store.sourceSink(item), fromStep, progressHook);
  }

  /**
   * 对可写目标执行字幕/ASR 或画面解析流水线。
   *
   * @param sink         落库目标
   * @param fromStep     起始步骤
   * @param progressHook 进度回调
   */
  public void parseSink(
      VideoParseSink sink,
      VideoParseStep fromStep,
      BiConsumer<String, String> progressHook) {
    pipeline.parseSink(sink, fromStep, progressHook);
  }

  /**
   * 用 yt-dlp 元数据探测时长；失败返回 null。
   *
   * @param url 视频页 URL
   * @return 秒数；失败为 null
   */
  public Double probeDurationSeconds(String url) {
    return durationCache.probeDurationSeconds(url);
  }
}
