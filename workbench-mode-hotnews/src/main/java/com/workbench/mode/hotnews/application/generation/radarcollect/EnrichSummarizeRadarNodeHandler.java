package com.workbench.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.workbench.mode.hotnews.facade.VideoTranscriptFacade;
import com.workbench.sourcing.facade.enums.SourceMediaType;
import com.workbench.infra.config.WorkbenchProperties;
import com.workbench.job.facade.JobFacade;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 对配额内保留的候选取正文/视频并生成事实速览。
 * <p>文章与视频分池并行：文章不被视频 ASR 占满线程；全部结束后才进入下一步。
 * <p>视频入池前按时长降序排队，优先消化长视频。
 */
@Slf4j
@Component
public class EnrichSummarizeRadarNodeHandler implements RadarCollectNodeHandler {
  /** 轮询等待间隔毫秒。 */
  private static final long POLL_INTERVAL_MS = 1000L;

  /** 解析支持。 */
  private final RadarCollectEnrichSupport enrichSupport;
  /** 视频时长探测（复用配额阶段缓存）。 */
  private final VideoTranscriptFacade videoTranscript;
  /** 任务进度上报。 */
  private final JobFacade jobs;
  /** 租约秒数等配置。 */
  private final WorkbenchProperties properties;
  /** 文章解析池。 */
  private final Executor radarArticleEnrichExecutor;
  /** 视频解析池。 */
  private final Executor radarVideoEnrichExecutor;

  /**
   * @param enrichSupport              解析支持
   * @param videoTranscript            视频转录（时长探测）
   * @param jobs                       任务门面
   * @param properties                 工作台配置
   * @param radarArticleEnrichExecutor 文章解析池
   * @param radarVideoEnrichExecutor   视频解析池
   */
  public EnrichSummarizeRadarNodeHandler(
      RadarCollectEnrichSupport enrichSupport,
      VideoTranscriptFacade videoTranscript,
      JobFacade jobs,
      WorkbenchProperties properties,
      @Qualifier("radarArticleEnrichExecutor") Executor radarArticleEnrichExecutor,
      @Qualifier("radarVideoEnrichExecutor") Executor radarVideoEnrichExecutor) {
    this.enrichSupport = enrichSupport;
    this.videoTranscript = videoTranscript;
    this.jobs = jobs;
    this.properties = properties;
    this.radarArticleEnrichExecutor = radarArticleEnrichExecutor;
    this.radarVideoEnrichExecutor = radarVideoEnrichExecutor;
  }

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.ENRICH_SUMMARIZE;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    // 兜底再裁一次（获取节点已前置；幂等）
    RadarCollectMediaQuotaSupport.applyCap(session);
    int maxArticle = session.getMaxArticlePerFetch();
    int maxVideo = session.getMaxVideoPerFetch();
    List<RadarCollectCandidate> articles = new ArrayList<>();
    List<RadarCollectCandidate> videos = new ArrayList<>();
    for (RadarCollectCandidate candidate : session.getCandidates()) {
      if (candidate == null || candidate.isDiscarded()) {
        continue;
      }
      if (SourceMediaType.from(candidate.getMediaType()).isVideo()) {
        videos.add(candidate);
      } else {
        articles.add(candidate);
      }
    }
    int articleCount = articles.size();
    int videoCount = videos.size();
    int total = articleCount + videoCount;
    String jobId = StrUtil.blankToDefault(
        stringVal(lightState.get(RadarCollectGraphStateKeys.JOB_ID)),
        session.getFetchRunId());
    String stepKey = HotNewsRadarCollectNodeType.ENRICH_SUMMARIZE.getValue();
    reportProgress(
        jobId,
        stepKey,
        40,
        progressMessage(0, articleCount, 0, videoCount));
    log.info(
        "雷达解析开始 article={} video={} articlePool 与 videoPool 隔离",
        articleCount,
        videoCount);

    AtomicInteger done = new AtomicInteger();
    AtomicInteger articleDone = new AtomicInteger();
    AtomicInteger videoDone = new AtomicInteger();
    List<CompletableFuture<Void>> futures = new ArrayList<>(total);
    // 文章先提交到文章池，不被视频 ASR 抢槽
    for (RadarCollectCandidate candidate : articles) {
      futures.add(CompletableFuture.runAsync(
          () -> enrichSafely(candidate, skillBindings, false, done, articleDone, videoDone),
          radarArticleEnrichExecutor));
    }
    // 视频按时长降序入队，线程池 FIFO 先消化长片
    prioritizeLongerVideos(videos);
    for (RadarCollectCandidate candidate : videos) {
      futures.add(CompletableFuture.runAsync(
          () -> enrichSafely(candidate, skillBindings, true, done, articleDone, videoDone),
          radarVideoEnrichExecutor));
    }

    // 主线程轮询：续租 + 进度；全部完成后才继续（屏障，保证下一步不提前）
    CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    while (!all.isDone()) {
      RadarCollectLeaseSupport.renew(lightState);
      reportProgress(
          jobId,
          stepKey,
          progressForDone(done.get(), total),
          progressMessage(
              articleDone.get(),
              articleCount,
              videoDone.get(),
              videoCount));
      if (StrUtil.isNotBlank(jobId)) {
        try {
          jobs.renewLease(jobId, properties.getWorkerLeaseSeconds());
        } catch (Exception exception) {
          log.warn("雷达解析租约续期失败 jobId={} err={}", jobId, exception.getMessage());
        }
      }
      try {
        all.get(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        all.cancel(true);
        throw new IllegalStateException("雷达解析被中断", interrupted);
      } catch (Exception ignored) {
        // 超时或单任务未完成：继续轮询；最终 join 再收口
      }
    }
    // 最终屏障：全部解析结束后才写 lightState / 进入下一节点
    all.join();

    List<RadarCollectCandidate> pending = new ArrayList<>(articles.size() + videos.size());
    pending.addAll(articles);
    pending.addAll(videos);
    int survivors = 0;
    int discarded = 0;
    for (RadarCollectCandidate candidate : pending) {
      if (candidate.isDiscarded()) {
        discarded++;
      } else {
        survivors++;
      }
    }
    ArticlePublishDateSummary publishDateSummary = summarizeArticlePublishDates(articles);
    String doneNote = "解析完成：共 "
        + total
        + " 条（文章 "
        + articleDone.get()
        + "/"
        + articleCount
        + "，视频 "
        + videoDone.get()
        + "/"
        + videoCount
        + "），成功 "
        + survivors
        + "，弃用 "
        + discarded;
    reportProgress(jobId, stepKey, 70, doneNote);
    lightState.put(RadarCollectGraphStateKeys.SURVIVOR_COUNT, survivors);
    lightState.put(RadarCollectGraphStateKeys.CANDIDATE_COUNT, session.getCandidates().size());
    lightState.put(RadarCollectGraphStateKeys.ENRICH_NOTE, doneNote);
    lightState.put(RadarCollectGraphStateKeys.ENRICH_PUBLISH_DATE_NOTE, publishDateSummary.note());
    lightState.put(
        RadarCollectGraphStateKeys.ENRICH_NO_CONTENT_URLS, publishDateSummary.noContentUrls());
    lightState.put(RadarCollectGraphStateKeys.ENRICH_NO_DATE_URLS, publishDateSummary.noDateUrls());
    lightState.put(RadarCollectGraphStateKeys.ENRICH_DISCARDED_COUNT, discarded);
    lightState.put(RadarCollectGraphStateKeys.ENRICH_ARTICLE_COUNT, articleCount);
    lightState.put(RadarCollectGraphStateKeys.ENRICH_VIDEO_COUNT, videoCount);
    log.info(
        "雷达解析速览完成 total={} survivors={} discarded={} articleCap={} videoCap={} publishDateStat={}",
        total,
        survivors,
        discarded,
        maxArticle,
        maxVideo,
        publishDateSummary.note());
    if (!publishDateSummary.noContentUrls().isEmpty()) {
      log.info(
          "雷达文章无正文URL count={} urls={}",
          publishDateSummary.noContentUrls().size(),
          String.join(" | ", publishDateSummary.noContentUrls()));
    }
    if (!publishDateSummary.noDateUrls().isEmpty()) {
      log.info(
          "雷达文章未抽到日期URL count={} urls={}",
          publishDateSummary.noDateUrls().size(),
          String.join(" | ", publishDateSummary.noDateUrls()));
    }
  }

  /**
   * 汇总本轮文章发布日来源计数与失败 URL，便于日志与 Job 状态排查。
   *
   * @param articles 本轮文章候选
   * @return 统计结果
   */
  private static ArticlePublishDateSummary summarizeArticlePublishDates(
      List<RadarCollectCandidate> articles) {
    int total = articles == null ? 0 : articles.size();
    Map<ArticlePublishDateSource, Integer> counts = new EnumMap<>(ArticlePublishDateSource.class);
    for (ArticlePublishDateSource source : ArticlePublishDateSource.values()) {
      counts.put(source, 0);
    }
    List<String> noContentUrls = new ArrayList<>();
    List<String> noDateUrls = new ArrayList<>();
    int withDate = 0;
    if (articles != null) {
      for (RadarCollectCandidate candidate : articles) {
        if (candidate == null) {
          continue;
        }
        ArticlePublishDateSource source = ArticlePublishDateSource.from(
            candidate.getPublishDateSource());
        counts.merge(source, 1, Integer::sum);
        if (candidate.getPublishedAt() != null || source.isResolved()) {
          withDate++;
        }
        String url = StrUtil.blankToDefault(candidate.getUrl(), "");
        if (StrUtil.isBlank(url)) {
          continue;
        }
        if (source == ArticlePublishDateSource.NO_CONTENT) {
          noContentUrls.add(url);
        } else if (source == ArticlePublishDateSource.NONE) {
          noDateUrls.add(url);
        }
      }
    }
    String note = "文章发布日 "
        + withDate
        + "/"
        + total
        + "（渠道 "
        + counts.get(ArticlePublishDateSource.CHANNEL)
        + "，正文AI "
        + counts.get(ArticlePublishDateSource.AI_CONTENT)
        + "，HTML补爬AI "
        + counts.get(ArticlePublishDateSource.AI_HTML)
        + "，元数据 "
        + counts.get(ArticlePublishDateSource.METADATA)
        + "，未抽到 "
        + counts.get(ArticlePublishDateSource.NONE)
        + "，无正文 "
        + counts.get(ArticlePublishDateSource.NO_CONTENT)
        + "）";
    return new ArticlePublishDateSummary(note, noContentUrls, noDateUrls);
  }

  /**
   * 文章发布日汇总。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  private static final class ArticlePublishDateSummary {
    /** 可读计数摘要。 */
    private final String note;
    /** 无正文 URL。 */
    private final List<String> noContentUrls;
    /** 有正文但未抽到日期 URL。 */
    private final List<String> noDateUrls;
  }

  /**
   * 按探测时长降序重排视频候选；未知时长视为 0，排在队尾。
   *
   * @param videos 待解析视频列表（原地排序）
   */
  private void prioritizeLongerVideos(List<RadarCollectCandidate> videos) {
    if (videos == null || videos.size() <= 1) {
      return;
    }
    videos.sort((left, right) -> Double.compare(
        durationSecondsOrZero(right.getUrl()),
        durationSecondsOrZero(left.getUrl())));
    RadarCollectCandidate first = videos.get(0);
    log.info(
        "雷达视频解析排队（时长优先） count={} firstTitle={} firstSec={}",
        videos.size(),
        first.getTitle(),
        durationSecondsOrZero(first.getUrl()));
  }

  /**
   * 探测视频时长秒数；失败或未知返回 0。
   *
   * @param url 视频页 URL
   * @return 秒数；未知为 0
   */
  private double durationSecondsOrZero(String url) {
    Double seconds = videoTranscript.probeDurationSeconds(url);
    return seconds == null || seconds <= 0 ? 0d : seconds;
  }

  /**
   * 单条安全解析：调用 enrichOne，异常则弃用；并记录分类型耗时。
   *
   * @param candidate     候选
   * @param skillBindings Skill 绑定
   * @param video         是否视频
   * @param done          合计已完成
   * @param articleDone   文章已完成
   * @param videoDone     视频已完成
   */
  private void enrichSafely(
      RadarCollectCandidate candidate,
      List<SkillPromptBindingView> skillBindings,
      boolean video,
      AtomicInteger done,
      AtomicInteger articleDone,
      AtomicInteger videoDone) {
    String mediaLabel = video ? "视频" : "文章";
    long startedAt = System.currentTimeMillis();
    log.info(
        "雷达解析开始 media={} title={} url={}",
        mediaLabel,
        candidate.getTitle(),
        candidate.getUrl());
    try {
      enrichSupport.enrichOne(candidate, skillBindings, video);
      long costMs = System.currentTimeMillis() - startedAt;
      if (candidate.isDiscarded()) {
        log.info(
            "雷达解析结束 media={} discarded=true costMs={} reason={} title={}",
            mediaLabel,
            costMs,
            candidate.getDiscardReason(),
            candidate.getTitle());
      } else {
        log.info(
            "雷达解析结束 media={} discarded=false costMs={} title={}",
            mediaLabel,
            costMs,
            candidate.getTitle());
      }
    } catch (Exception exception) {
      candidate.setDiscarded(true);
      candidate.setDiscardReason(
          "解析异常：" + StrUtil.sub(StrUtil.nullToEmpty(exception.getMessage()), 0, 120));
      candidate.setRawText(null);
      log.warn(
          "雷达并行解析异常 media={} costMs={} title={} err={}",
          mediaLabel,
          System.currentTimeMillis() - startedAt,
          candidate.getTitle(),
          exception.getMessage());
    } finally {
      done.incrementAndGet();
      if (video) {
        videoDone.incrementAndGet();
      } else {
        articleDone.incrementAndGet();
      }
    }
  }

  /**
   * 进行中进度文案：左侧为已解析，右侧为该类待解析总数。
   *
   * @param articleFinished 文章已解析
   * @param articleTotal    文章总数
   * @param videoFinished   视频已解析
   * @param videoTotal      视频总数
   * @return 文案
   */
  private static String progressMessage(
      int articleFinished,
      int articleTotal,
      int videoFinished,
      int videoTotal) {
    int finished = articleFinished + videoFinished;
    int total = articleTotal + videoTotal;
    return "已解析 "
        + finished
        + "/"
        + total
        + "（文章 "
        + articleFinished
        + "/"
        + articleTotal
        + "，视频 "
        + videoFinished
        + "/"
        + videoTotal
        + "）";
  }

  /**
   * 按已完成数映射进度百分比（约 40～68）。
   *
   * @param finished 已完成条数
   * @param total    总条数
   * @return 进度
   */
  private static int progressForDone(int finished, int total) {
    if (total <= 0) {
      return 55;
    }
    return 40 + Math.round(Math.min(finished, total) * 28f / total);
  }

  /**
   * 上报 Job 进度事件，供执行页小字读取。
   *
   * @param jobId    任务 ID
   * @param stepKey  节点 typeKey
   * @param progress 进度
   * @param message  文案
   */
  private void reportProgress(String jobId, String stepKey, int progress, String message) {
    if (StrUtil.isBlank(jobId)) {
      return;
    }
    try {
      jobs.updateProgress(jobId, stepKey, Math.min(90, Math.max(1, progress)), message);
    } catch (Exception exception) {
      log.warn("雷达解析进度上报失败 jobId={} err={}", jobId, exception.getMessage());
    }
  }

  /**
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String stringVal(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
