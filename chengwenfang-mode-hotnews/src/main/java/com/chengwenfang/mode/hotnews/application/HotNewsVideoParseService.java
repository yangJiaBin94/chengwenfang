package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.mode.hotnews.application.videoparse.SourceItemVideoParseStrategy;
import com.chengwenfang.sourcing.application.videoparse.SourceVideoParseItemOutcome;
import com.chengwenfang.mode.hotnews.application.videoparse.TopicResearchItemVideoParseStrategy;
import com.chengwenfang.sourcing.application.videoparse.VideoParseOwnerStrategyResolver;
import com.chengwenfang.sourcing.application.videoparse.VideoParseProgressSupport;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.mode.hotnews.facade.dto.request.VideoParseRequest;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobEntityTypes;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.HotNewsSourceVideoParseJobInput;
import com.chengwenfang.job.facade.dto.job.HotNewsSourceVideoParseJobOutput;
import com.chengwenfang.job.facade.dto.job.HotNewsVideoParseJobInput;
import com.chengwenfang.job.facade.dto.job.HotNewsVideoParseJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.job.facade.dto.view.JobView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点视频解析编排器：Job 入队与执行；归属差异委托 {@link TopicResearchItemVideoParseStrategy} /
 * {@link SourceItemVideoParseStrategy}。
 * <p>由 {@link HotNewsResearchService} 薄委托；不对外暴露 Facade。
 */
@Slf4j
@Service
public class HotNewsVideoParseService {

  /** 任务门面。 */
  private final JobFacade jobs;
  /** 字幕/时长能力开关与探测。 */
  private final VideoTranscriptService videoTranscriptService;
  /** 成文坊配置（单批上限等）。 */
  private final ChengwenfangProperties chengwenfangProperties;
  /** 资料池并行解析线程池。 */
  private final Executor radarVideoEnrichExecutor;
  /** 选题协作（存在性校验）。 */
  private final HotNewsTopicService topics;
  /** 资料池条目策略（owner 特有流程）。 */
  private final TopicResearchItemVideoParseStrategy researchItemStrategy;
  /** 雷达信源策略（owner 特有流程）。 */
  private final SourceItemVideoParseStrategy sourceItemStrategy;
  /** 归属策略解析器（markFailed 等通用路径）。 */
  private final VideoParseOwnerStrategyResolver ownerStrategies;

  /**
   * @param jobs                     任务门面
   * @param videoTranscriptService   视频字幕/时长
   * @param chengwenfangProperties      成文坊配置
   * @param radarVideoEnrichExecutor 视频并行解析线程池
   * @param topics                   选题协作
   * @param researchItemStrategy     资料池策略
   * @param sourceItemStrategy       信源策略
   * @param ownerStrategies          归属策略解析器
   */
  public HotNewsVideoParseService(
      JobFacade jobs,
      VideoTranscriptService videoTranscriptService,
      ChengwenfangProperties chengwenfangProperties,
      @Qualifier("radarVideoEnrichExecutor") Executor radarVideoEnrichExecutor,
      @Lazy HotNewsTopicService topics,
      TopicResearchItemVideoParseStrategy researchItemStrategy,
      SourceItemVideoParseStrategy sourceItemStrategy,
      VideoParseOwnerStrategyResolver ownerStrategies) {
    this.jobs = jobs;
    this.videoTranscriptService = videoTranscriptService;
    this.chengwenfangProperties = chengwenfangProperties;
    this.radarVideoEnrichExecutor = radarVideoEnrichExecutor;
    this.topics = topics;
    this.researchItemStrategy = researchItemStrategy;
    this.sourceItemStrategy = sourceItemStrategy;
    this.ownerStrategies = ownerStrategies;
  }

  /**
   * 启动资料池视频解析任务。
   *
   * @param topicId 选题主键
   * @param request 条目与起始步骤
   * @return 任务视图
   */
  @Transactional
  public JobView startVideoParse(String topicId, VideoParseRequest request) {
    topics.requireTopic(topicId);
    if (!videoTranscriptService.isEnabled()) {
      throw new BusinessException(
          ErrorCodes.VIDEO_PARSE_DISABLED,
          "视频解析未启用，请检查本机依赖与 chengwenfang.video-transcript.enabled",
          400);
    }
    VideoParseRequest body = request == null ? new VideoParseRequest() : request;
    HotNewsVideoParseJobInput.HotNewsVideoParseJobInputBuilder inputBuilder =
        HotNewsVideoParseJobInput.builder().topicId(topicId);
    if (body.getItemIds() != null && !body.getItemIds().isEmpty()) {
      inputBuilder.itemIds(body.getItemIds());
    }
    if (StrUtil.isNotBlank(body.getFromStep())) {
      inputBuilder.fromStep(body.getFromStep().trim());
    }
    return jobs.create(
        JobTypes.HOT_NEWS_VIDEO_PARSE,
        JobEntityTypes.TOPIC_RESEARCH_RUN,
        topicId,
        inputBuilder.build());
  }

  /**
   * 执行资料池视频解析 Job：经线程池并行处理，单条失败不阻断整批。
   *
   * @param jobId     任务 ID
   * @param inputJson 输入 JSON
   * @param context   进度上下文
   */
  public void executeVideoParseJob(String jobId, String inputJson, JobExecutionContext context) {
    HotNewsVideoParseJobInput parsedInput =
        JobPayloads.parse(inputJson, HotNewsVideoParseJobInput.class);
    String topicId = StrUtil.nullToEmpty(parsedInput.getTopicId());
    VideoParseStep fromStep = VideoParseStep.from(parsedInput.getFromStep());
    List<String> itemIds = parsedInput.getItemIds();
    if (StrUtil.isBlank(topicId)) {
      throw new IllegalArgumentException("缺少 topicId");
    }
    List<TopicResearchItemEntity> targets =
        researchItemStrategy.loadParseTargets(topicId, itemIds);
    int max = Math.max(1, chengwenfangProperties.getVideoTranscript().getMaxPerTopic());
    if (targets.size() > max) {
      targets = targets.subList(0, max);
    }
    if (targets.isEmpty()) {
      context.progress(jobId, "done", 100, "没有需要解析的视频");
      context.completeWith(HotNewsVideoParseJobOutput.builder()
          .ok(true)
          .parsed(0)
          .failed(0)
          .build());
      return;
    }
    // 按时长降序入队，线程池 FIFO 优先消化长片
    researchItemStrategy.prioritizeByDuration(targets);
    int total = targets.size();
    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();
    AtomicInteger maxProgress = new AtomicInteger(0);
    /** 进行中条目：itemId → 短标题，供并行进度展示。 */
    Map<String, String> activeTitles = new LinkedHashMap<>();
    Object progressLock = new Object();
    List<CompletableFuture<Void>> futures = new ArrayList<>(total);
    for (int index = 0; index < total; index++) {
      TopicResearchItemEntity item = targets.get(index);
      String title = StrUtil.blankToDefault(item.getTitle(), item.getUrl());
      String shortTitle = StrUtil.maxLength(title, 48);
      futures.add(CompletableFuture.runAsync(() -> {
        if (context.isCancelled(jobId)) {
          return;
        }
        // 整条包在 try 内：单条失败（含进度上报异常）不中断同批其他素材
        try {
          synchronized (progressLock) {
            activeTitles.put(item.getId(), shortTitle);
          }
          VideoParseProgressSupport.reportLocked(
              context,
              jobId,
              VideoParseStep.FETCH_SUBTITLE.getValue(),
              total,
              succeeded.get() + failed.get(),
              activeTitles,
              maxProgress,
              progressLock);
          try {
            context.renewLease(jobId);
          } catch (Exception renewError) {
            log.warn("视频解析续租失败 jobId={} err={}", jobId, renewError.getMessage());
          }
          TopicResearchItemEntity fresh = researchItemStrategy.reload(item.getId());
          if (fresh == null) {
            failed.incrementAndGet();
            return;
          }
          researchItemStrategy.parseOne(
              fresh,
              fromStep,
              (step, message) -> {
                try {
                  VideoParseProgressSupport.reportLocked(
                      context,
                      jobId,
                      step,
                      total,
                      succeeded.get() + failed.get(),
                      activeTitles,
                      maxProgress,
                      progressLock);
                } catch (Exception progressError) {
                  // 进度上报失败不影响本条解析与同批其他条目
                  log.warn("视频解析进度上报失败 itemId={} err={}",
                      fresh.getId(), progressError.getMessage());
                }
              });
          TopicResearchItemEntity after = researchItemStrategy.reload(item.getId());
          if (researchItemStrategy.isSucceeded(after)) {
            succeeded.incrementAndGet();
          } else {
            failed.incrementAndGet();
          }
        } catch (Exception exception) {
          log.warn("视频解析单条失败，继续同批其他条目 itemId={} err={}",
              item.getId(), exception.getMessage());
          // 通用失败标记经 resolver 按 ownerType 路由
          ownerStrategies.markFailed(
              VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM,
              item.getId(),
              exception.getMessage());
          failed.incrementAndGet();
        } finally {
          synchronized (progressLock) {
            activeTitles.remove(item.getId());
          }
          try {
            VideoParseProgressSupport.reportLocked(
                context,
                jobId,
                "batch",
                total,
                succeeded.get() + failed.get(),
                activeTitles,
                maxProgress,
                progressLock);
          } catch (Exception progressError) {
            log.warn("视频解析收尾进度上报失败 itemId={} err={}",
                item.getId(), progressError.getMessage());
          }
        }
      }, radarVideoEnrichExecutor));
    }
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    int parsedCount = succeeded.get();
    int failedCount = failed.get();
    Map<String, Object> doneMeta = new LinkedHashMap<>();
    doneMeta.put("total", total);
    doneMeta.put("completed", parsedCount + failedCount);
    doneMeta.put("parsed", parsedCount);
    doneMeta.put("failed", failedCount);
    doneMeta.put("done", true);
    context.progress(
        jobId,
        "done",
        100,
        "解析结束：成功 " + parsedCount + "，失败 " + failedCount,
        doneMeta);
    context.completeWith(HotNewsVideoParseJobOutput.builder()
        .ok(true)
        .parsed(parsedCount)
        .failed(failedCount)
        .total(total)
        .build());
  }

  /**
   * 为视频候选写入时长秒数（优先缓存，必要时再探测）。
   *
   * @param candidates 待入库候选
   */
  public void stampVideoDurations(List<ResearchCandidate> candidates) {
    if (CollUtil.isEmpty(candidates)) {
      return;
    }
    for (ResearchCandidate candidate : candidates) {
      if (candidate == null || candidate.getVideoDurationSeconds() != null) {
        continue;
      }
      if (!ResearchContentClassifier.isVideo(candidate.getUrl(), candidate.getTitle())) {
        continue;
      }
      candidate.setVideoDurationSeconds(
          videoTranscriptService.resolveDurationSeconds(candidate.getUrl()));
    }
  }

  /**
   * 为待解析雷达视频入队自动解析任务。
   *
   * @param themeId 主题 ID
   * @return 任务；无需解析时 null
   */
  @Transactional
  public JobView enqueuePendingSourceVideoParse(String themeId) {
    if (StrUtil.isBlank(themeId) || !videoTranscriptService.isEnabled()) {
      return null;
    }
    if (sourceItemStrategy.countPending(themeId) <= 0) {
      return null;
    }
    return jobs.create(
        JobTypes.HOT_NEWS_SOURCE_VIDEO_PARSE,
        JobEntityTypes.THEME,
        themeId,
        HotNewsSourceVideoParseJobInput.builder().themeId(themeId).build());
  }

  /**
   * 执行雷达信源视频解析 Job。
   *
   * @param jobId     任务 ID
   * @param inputJson 输入
   * @param context   进度上下文
   */
  public void executeSourceVideoParseJob(String jobId, String inputJson, JobExecutionContext context) {
    HotNewsSourceVideoParseJobInput parsedInput =
        JobPayloads.parse(inputJson, HotNewsSourceVideoParseJobInput.class);
    String themeId = StrUtil.nullToEmpty(parsedInput.getThemeId());
    if (StrUtil.isBlank(themeId)) {
      throw new IllegalArgumentException("缺少 themeId");
    }
    if (!videoTranscriptService.isEnabled()) {
      // 未启用则把待解析视频全部弃用，避免长期 pending 污染
      List<SourceItemEntity> pending = sourceItemStrategy.loadAllPendingParse(themeId);
      for (SourceItemEntity item : pending) {
        sourceItemStrategy.discard(item, "视频解析未启用", null);
      }
      context.completeWith(HotNewsSourceVideoParseJobOutput.builder()
          .ok(true)
          .parsed(0)
          .discarded(pending.size())
          .build());
      return;
    }
    int max = Math.max(1, chengwenfangProperties.getVideoTranscript().getMaxPerTopic());
    List<SourceItemEntity> targets = sourceItemStrategy.loadPendingTargets(themeId, max);
    if (targets.isEmpty()) {
      context.progress(jobId, "done", 100, "没有待解析的雷达视频");
      context.completeWith(HotNewsSourceVideoParseJobOutput.builder()
          .ok(true)
          .parsed(0)
          .discarded(0)
          .build());
      return;
    }
    int ready = 0;
    int discarded = 0;
    int total = targets.size();
    for (int index = 0; index < total; index++) {
      if (context.isCancelled(jobId)) {
        break;
      }
      SourceItemEntity item = targets.get(index);
      String title = StrUtil.blankToDefault(item.getTitle(), item.getUrl());
      context.progress(
          jobId,
          "parse",
          (int) Math.round(100.0 * index / Math.max(1, total)),
          "解析视频 " + (index + 1) + "/" + total + "：" + StrUtil.maxLength(title, 40));
      try {
        context.renewLease(jobId);
      } catch (Exception renewError) {
        log.warn("雷达视频解析续租失败 jobId={} err={}", jobId, renewError.getMessage());
      }
      try {
        SourceVideoParseItemOutcome outcome = sourceItemStrategy.processOne(themeId, item);
        if (outcome == SourceVideoParseItemOutcome.READY) {
          ready++;
        } else {
          discarded++;
        }
      } catch (Exception exception) {
        log.warn("雷达视频解析单条失败 itemId={} err={}", item.getId(), exception.getMessage());
        SourceItemEntity failed = sourceItemStrategy.reload(item.getId());
        sourceItemStrategy.discard(
            failed == null ? item : failed, exception.getMessage(), null);
        discarded++;
      }
    }
    // 超额未处理的 pending 本轮保留，下次采集再入队
    context.completeWith(HotNewsSourceVideoParseJobOutput.builder()
        .ok(true)
        .ready(ready)
        .discarded(discarded)
        .total(total)
        .build());
  }

  /**
   * 资料池口播原文：内存优先，否则读共享表。
   *
   * @param item 资料池条目
   * @return 原文；可空
   */
  public String resolveResearchVideoRawText(TopicResearchItemEntity item) {
    return researchItemStrategy.resolveRawText(item);
  }

  /**
   * 信源解析状态与口播原文补齐到内存字段。
   *
   * @param item 信源实体
   */
  public void hydrateSourceVideoRawText(SourceItemEntity item) {
    sourceItemStrategy.hydrate(item);
  }
}
