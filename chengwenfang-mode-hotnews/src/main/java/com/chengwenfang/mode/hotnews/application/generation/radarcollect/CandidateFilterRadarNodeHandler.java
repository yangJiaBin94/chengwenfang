package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.ThemeTitleRelevance;
import com.chengwenfang.crawler.facade.MarketingPageFilter;
import com.chengwenfang.sourcing.facade.SourceVideoTitleFilter;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 素材筛选：主题相关性、硬广/营销等可扩展过滤；命中则弃用且只打日志。
 * <p>规则过滤串行；AI 营销判定有界并行，避免 40+ 条串行拖死整步。
 */
@Slf4j
@Component
public class CandidateFilterRadarNodeHandler implements RadarCollectNodeHandler {
  /** AI 营销判定。 */
  private final RadarCollectAiSupport aiSupport;
  /** 任务进度。 */
  private final JobFacade jobs;
  /** 租约配置。 */
  private final ChengwenfangProperties properties;
  /** 复用文章解析池做营销 AI 并行。 */
  private final Executor filterExecutor;

  /**
   * @param aiSupport      AI 支持
   * @param jobs           任务门面
   * @param properties     配置
   * @param filterExecutor 并行执行器
   */
  public CandidateFilterRadarNodeHandler(
      RadarCollectAiSupport aiSupport,
      JobFacade jobs,
      ChengwenfangProperties properties,
      @Qualifier("radarArticleEnrichExecutor") Executor filterExecutor) {
    this.aiSupport = aiSupport;
    this.jobs = jobs;
    this.properties = properties;
    this.filterExecutor = filterExecutor;
  }

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.CANDIDATE_FILTER;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    String jobId = StrUtil.blankToDefault(
        stringVal(lightState.get(RadarCollectGraphStateKeys.JOB_ID)),
        session.getFetchRunId());
    String stepKey = HotNewsRadarCollectNodeType.CANDIDATE_FILTER.getValue();
    List<RadarCollectCandidate> pending = new ArrayList<>();
    for (RadarCollectCandidate candidate : session.getCandidates()) {
      if (candidate != null && !candidate.isDiscarded()) {
        pending.add(candidate);
      }
    }
    int inputCount = pending.size();
    reportProgress(jobId, stepKey, 72, "素材筛选：参与 " + inputCount + " 条…");

    // 1) 本地规则先拦硬广和空标题。词典未命中不在这里丢，整包问模型
    List<RadarCollectCandidate> needMarketing = new ArrayList<>();
    List<RadarCollectCandidate> needThemeModel = new ArrayList<>();
    int ruleScanned = 0;
    for (RadarCollectCandidate candidate : pending) {
      ruleScanned++;
      RadarCollectLeaseSupport.renew(lightState);
      ThemeTitleRelevance.Decision themeDecision = ThemeTitleRelevance.judge(
          session.getThemeRelevanceProfile(),
          candidate.getTitle(),
          candidate.getFactBrief());
      if (themeDecision.hardDrop()) {
        discard(candidate, "主题不相关：" + themeDecision.reason());
      } else if (MarketingPageFilter.shouldSkip(candidate.getUrl(), candidate.getTitle())
          || SourceVideoTitleFilter.looksLikeHardSellTitle(candidate.getTitle())) {
        discard(candidate, "标题硬广/推销");
      } else if (themeDecision.uncertain()) {
        needThemeModel.add(candidate);
      } else {
        needMarketing.add(candidate);
      }
      if (ruleScanned % 8 == 0 || ruleScanned == inputCount) {
        reportProgress(
            jobId,
            stepKey,
            progressFor(ruleScanned, inputCount, 72, 74),
            "素材筛选：规则过滤 "
                + ruleScanned
                + " / "
                + inputCount
                + "，待主题判定 "
                + needThemeModel.size()
                + " 条，待营销判定 "
                + needMarketing.size()
                + " 条");
      }
    }

    if (!needThemeModel.isEmpty()) {
      reportProgress(
          jobId,
          stepKey,
          74,
          "素材筛选：主题相关性判定 " + needThemeModel.size() + " 条…");
      Set<String> offTopicIds = aiSupport.findOffTopicIds(
          session.getThemeRelevanceProfile(), needThemeModel);
      for (RadarCollectCandidate candidate : needThemeModel) {
        if (offTopicIds.contains(candidate.getTempId())) {
          discard(candidate, "AI 判定主题不相关");
        } else {
          needMarketing.add(candidate);
        }
      }
    }

    // 2) AI 营销有界并行
    List<RadarCollectCandidate> needAi = needMarketing;
    AtomicInteger aiDone = new AtomicInteger();
    int aiTotal = needAi.size();
    if (aiTotal > 0) {
      reportProgress(
          jobId,
          stepKey,
          75,
          "素材筛选：AI 营销判定 0 / " + aiTotal + "…");
      List<CompletableFuture<Void>> futures = new ArrayList<>(aiTotal);
      for (RadarCollectCandidate candidate : needAi) {
        futures.add(CompletableFuture.runAsync(() -> {
          try {
            boolean marketing = aiSupport.isMarketing(
                skillBindings,
                candidate.getTitle(),
                candidate.getFactBrief(),
                candidate.getRawText());
            if (marketing) {
              discard(candidate, "AI 判定营销内容");
            }
          } catch (Exception exception) {
            log.warn(
                "雷达营销判定异常 title={} err={}",
                candidate.getTitle(),
                exception.getMessage());
          } finally {
            int finished = aiDone.incrementAndGet();
            if (finished % 3 == 0 || finished == aiTotal) {
              RadarCollectLeaseSupport.renew(lightState);
              reportProgress(
                  jobId,
                  stepKey,
                  progressFor(finished, aiTotal, 75, 77),
                  "素材筛选：AI 营销判定 "
                      + finished
                      + " / "
                      + aiTotal);
              if (StrUtil.isNotBlank(jobId)) {
                try {
                  jobs.renewLease(jobId, properties.getWorkerLeaseSeconds());
                } catch (Exception renewError) {
                  log.warn("素材筛选租约续期失败 jobId={} err={}", jobId, renewError.getMessage());
                }
              }
            }
          }
        }, filterExecutor));
      }
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    int survivors = session.survivors().size();
    int discarded = Math.max(0, inputCount - survivors);
    String note = "素材筛选：参与 "
        + inputCount
        + " 条，弃用拦截 "
        + discarded
        + " 条，通过保留 "
        + survivors
        + " 条";
    lightState.put(RadarCollectGraphStateKeys.SURVIVOR_COUNT, survivors);
    lightState.put(RadarCollectGraphStateKeys.FILTER_INPUT_COUNT, inputCount);
    lightState.put(RadarCollectGraphStateKeys.FILTER_DISCARDED_COUNT, discarded);
    lightState.put(RadarCollectGraphStateKeys.FILTER_NOTE, note);
    reportProgress(jobId, stepKey, 78, note);
    if (StrUtil.isNotBlank(jobId)) {
      try {
        jobs.renewLease(jobId, properties.getWorkerLeaseSeconds());
      } catch (Exception exception) {
        log.warn("素材筛选租约续期失败 jobId={} err={}", jobId, exception.getMessage());
      }
    }
    log.info("雷达素材筛选完成 input={} discarded={} survivors={}", inputCount, discarded, survivors);
  }

  /**
   * 将完成数映射到进度区间。
   *
   * @param finished 已完成
   * @param total    总数
   * @param from     起点进度
   * @param to       终点进度
   * @return 进度
   */
  private static int progressFor(int finished, int total, int from, int to) {
    if (total <= 0) {
      return to;
    }
    return from + (int) Math.round((to - from) * (finished * 1.0 / total));
  }

  /**
   * 标记弃用。
   *
   * @param candidate 候选
   * @param reason    原因
   */
  private static void discard(RadarCollectCandidate candidate, String reason) {
    candidate.setDiscarded(true);
    if (StrUtil.contains(reason, "营销") || StrUtil.contains(reason, "硬广") || StrUtil.contains(reason, "推销")) {
      candidate.setMarketing(true);
    }
    candidate.setDiscardReason(reason);
    candidate.setRawText(null);
    log.info("雷达素材弃用 title={} reason={}", candidate.getTitle(), reason);
  }

  /**
   * 上报进度。
   *
   * @param jobId    任务 ID
   * @param stepKey  步骤键
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
      log.warn("素材筛选进度上报失败 jobId={} err={}", jobId, exception.getMessage());
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
