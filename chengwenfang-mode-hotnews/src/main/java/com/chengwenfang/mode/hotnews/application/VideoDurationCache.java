package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.sourcing.application.VideoTranscriptCliRunner;
import com.chengwenfang.sourcing.application.model.VideoTranscriptCliResult;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 视频时长进程内缓存与 yt-dlp 探测。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoDurationCache {
  /** 同进程内 URL → 时长秒数缓存（避免配额裁剪与解析各探测一次）。 */
  private final ConcurrentHashMap<String, Double> durationSecondsCache = new ConcurrentHashMap<>();

  /** 成文坊配置。 */
  private final ChengwenfangProperties properties;
  /** CLI 子进程执行接缝。 */
  private final VideoTranscriptCliRunner cliRunner;

  /**
   * 配置的最大允许时长（分钟）；{@code <= 0} 表示不限制。
   *
   * @return 分钟数
   */
  public int maxDurationMinutes() {
    ChengwenfangProperties.VideoTranscriptSettings settings = properties.getVideoTranscript();
    return settings == null ? 0 : settings.getMaxDurationMinutes();
  }

  /**
   * 若 URL 对应视频超过时长上限，返回弃用/失败原因；未超限、未配置上限或探测失败时返回 {@code null}。
   *
   * @param url 视频页 URL
   * @return 超限说明；可继续解析时为 null
   */
  public String durationLimitRejectReason(String url) {
    int maxMinutes = maxDurationMinutes();
    if (maxMinutes <= 0 || StrUtil.isBlank(url)) {
      return null;
    }
    Double seconds = probeDurationSeconds(url);
    if (seconds == null || seconds <= 0) {
      // 探测失败不拦截，交给后续字幕/ASR 流程
      return null;
    }
    double minutes = seconds / 60.0;
    if (minutes <= maxMinutes) {
      return null;
    }
    return "视频时长超过上限（"
        + formatMinutes(minutes)
        + " 分钟 > "
        + maxMinutes
        + " 分钟），已跳过";
  }

  /**
   * 仅读进程内时长缓存，不触发 yt-dlp。
   *
   * @param url 视频页 URL
   * @return 缓存秒数；未探测过为 null
   */
  public Double peekCachedDurationSeconds(String url) {
    if (StrUtil.isBlank(url)) {
      return null;
    }
    return durationSecondsCache.get(url);
  }

  /**
   * 解析可展示的整数秒：优先缓存，否则探测；失败返回 null。
   *
   * @param url 视频页 URL
   * @return 秒数；未知为 null
   */
  public Integer resolveDurationSeconds(String url) {
    Double cached = peekCachedDurationSeconds(url);
    if (cached != null && cached > 0) {
      return (int) Math.round(cached);
    }
    Double probed = probeDurationSeconds(url);
    if (probed == null || probed <= 0) {
      return null;
    }
    return (int) Math.round(probed);
  }

  /**
   * 将探测到的秒数取整为可落库整数。
   *
   * @param seconds 探测秒数
   * @return 正整数秒；无效为 null
   */
  public static Integer toRoundedDurationSeconds(Double seconds) {
    if (seconds == null || seconds <= 0) {
      return null;
    }
    return (int) Math.round(seconds);
  }

  /**
   * 用 yt-dlp 元数据探测时长；失败返回 null。
   *
   * @param url 视频页 URL
   * @return 秒数；失败为 null
   */
  public Double probeDurationSeconds(String url) {
    if (StrUtil.isBlank(url) || !isEnabled()) {
      return null;
    }
    Double cached = durationSecondsCache.get(url);
    if (cached != null) {
      return cached;
    }
    Path workDir = workDirFor("probe-" + Integer.toHexString(url.hashCode()));
    try {
      Files.createDirectories(workDir);
    } catch (Exception exception) {
      log.warn("创建时长探测目录失败 url={} err={}", url, exception.getMessage());
      return null;
    }
    VideoTranscriptCliResult result = cliRunner.run(url, workDir, VideoParseStep.PROBE_DURATION);
    if (result == null || !result.isOk() || result.getDurationSeconds() == null) {
      log.info(
          "视频时长探测失败 url={} err={}",
          url,
          result == null ? "null" : StrUtil.blankToDefault(result.getError(), "无 duration"));
      return null;
    }
    durationSecondsCache.put(url, result.getDurationSeconds());
    return result.getDurationSeconds();
  }

  /**
   * 解析能力是否开启。
   *
   * @return 已启用时为 true
   */
  public boolean isEnabled() {
    return properties.getVideoTranscript() != null && properties.getVideoTranscript().isEnabled();
  }

  /**
   * 将分钟数格式化为展示字符串（整数不带小数，否则一位小数）。
   *
   * @param minutes 分钟
   * @return 展示文本
   */
  private static String formatMinutes(double minutes) {
    if (Math.abs(minutes - Math.rint(minutes)) < 0.05) {
      return String.valueOf((int) Math.rint(minutes));
    }
    return String.format(java.util.Locale.ROOT, "%.1f", minutes);
  }

  /**
   * 条目工作目录路径。
   *
   * @param itemId 条目或探测用标识
   * @return 工作目录
   */
  private Path workDirFor(String itemId) {
    Path runtime = Path.of(properties.getRuntimeDir()).toAbsolutePath().normalize();
    return runtime.resolve("video-transcript").resolve(itemId);
  }
}
