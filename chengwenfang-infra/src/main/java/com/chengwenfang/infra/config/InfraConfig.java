package com.chengwenfang.infra.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 基础设施配置：启用 {@link ChengwenfangProperties}，并注册全局线程池 Bean。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ChengwenfangProperties.class)
public class InfraConfig {

  /**
   * 渠道抓取专用线程池（Handler fan-out 与搜索策略多 query 共用）。
   *
   * @param properties 工作台配置
   * @return 可注入的 {@link Executor}
   */
  @Bean(name = "channelFetchExecutor", destroyMethod = "shutdown")
  public Executor channelFetchExecutor(ChengwenfangProperties properties) {
    ChengwenfangProperties.ChannelFetchExecutorSettings settings =
        properties.getExecutor().getChannelFetch();
    return buildExecutor(
        settings.getCorePoolSize(),
        settings.getMaxPoolSize(),
        settings.getQueueCapacity(),
        settings.getKeepAliveSeconds(),
        settings.getThreadNamePrefix(),
        "渠道采集");
  }

  /**
   * 雷达文章解析线程池（拉正文 + AI 速览）。
   *
   * @param properties 工作台配置
   * @return 可注入的 {@link Executor}
   */
  @Bean(name = "radarArticleEnrichExecutor", destroyMethod = "shutdown")
  public Executor radarArticleEnrichExecutor(ChengwenfangProperties properties) {
    ChengwenfangProperties.RadarArticleEnrichExecutorSettings settings =
        properties.getExecutor().getRadarArticleEnrich();
    return buildExecutor(
        settings.getCorePoolSize(),
        settings.getMaxPoolSize(),
        settings.getQueueCapacity(),
        settings.getKeepAliveSeconds(),
        settings.getThreadNamePrefix(),
        "雷达文章解析");
  }

  /**
   * 雷达视频解析线程池（字幕/ASR，与文章池隔离，避免占满文章槽位）。
   *
   * @param properties 工作台配置
   * @return 可注入的 {@link Executor}
   */
  @Bean(name = "radarVideoEnrichExecutor", destroyMethod = "shutdown")
  public Executor radarVideoEnrichExecutor(ChengwenfangProperties properties) {
    ChengwenfangProperties.RadarVideoEnrichExecutorSettings settings =
        properties.getExecutor().getRadarVideoEnrich();
    return buildExecutor(
        settings.getCorePoolSize(),
        settings.getMaxPoolSize(),
        settings.getQueueCapacity(),
        settings.getKeepAliveSeconds(),
        settings.getThreadNamePrefix(),
        "雷达视频解析");
  }

  /**
   * 构建有界线程池；队列满时 CallerRuns 背压。
   *
   * @param corePoolSize     核心线程
   * @param maxPoolSize      最大线程
   * @param queueCapacity    队列容量
   * @param keepAliveSeconds 保活秒数
   * @param threadNamePrefix 线程名前缀
   * @param logLabel         日志标签
   * @return Executor
   */
  private static Executor buildExecutor(
      int corePoolSize,
      int maxPoolSize,
      int queueCapacity,
      int keepAliveSeconds,
      String threadNamePrefix,
      String logLabel) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setKeepAliveSeconds(keepAliveSeconds);
    executor.setThreadNamePrefix(threadNamePrefix);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    log.info("{}线程池已初始化 核心={} 最大={} 队列={} 前缀={}",
        logLabel, corePoolSize, maxPoolSize, queueCapacity, threadNamePrefix);
    return executor;
  }
}
