package com.chengwenfang.mode.hotnews.application.hotnews;

import com.chengwenfang.mode.hotnews.facade.VideoTranscriptFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 视频解析本地工作目录清理：启动时执行一次，之后每小时再扫一次，删除超过保留天数的产物。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscriptWorkDirCleanupScheduler implements ApplicationRunner {
  /** 视频解析门面。 */
  private final VideoTranscriptFacade videoTranscript;

  /**
   * 应用启动后清理一次超期目录。
   *
   * @param args 启动参数
   */
  @Override
  public void run(ApplicationArguments args) {
    runCleanup("startup");
  }

  /** 每小时清理一次超期目录。 */
  @Scheduled(fixedDelayString = "3600000", initialDelayString = "3600000")
  public void cleanupHourly() {
    runCleanup("hourly");
  }

  /**
   * 执行清理并打日志。
   *
   * @param trigger 触发来源
   */
  private void runCleanup(String trigger) {
    try {
      int removed = videoTranscript.cleanupExpiredWorkDirs();
      if (removed > 0) {
        log.info("视频解析工作目录清理完成 trigger={} removedDirs={}", trigger, removed);
      } else {
        log.debug("视频解析工作目录清理无过期目录 trigger={}", trigger);
      }
    } catch (Exception exception) {
      log.warn("视频解析工作目录清理失败 trigger={} err={}", trigger, exception.getMessage());
    }
  }
}
