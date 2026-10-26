package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsResearchPoolLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * 资料补充配额：超长视频剔除，再按文章/视频条数上限裁剪。
 */
@Slf4j
public final class HotNewsResearchPoolQuotaSupport {
  private HotNewsResearchPoolQuotaSupport() {}

  /**
   * 先跳过超长视频（不占配额），再分别裁到文章/视频上限；保持原顺序优先。
   *
   * @param candidates            候选列表
   * @param videoDurationRejector 视频 URL → 弃用原因；空表示可保留；可为 null
   * @return 裁剪后的列表
   */
  public static List<ResearchCandidate> applyLimits(
      List<ResearchCandidate> candidates,
      Function<String, String> videoDurationRejector) {
    if (CollUtil.isEmpty(candidates)) {
      return List.of();
    }
    List<ResearchCandidate> kept = new ArrayList<>();
    int articleKept = 0;
    int videoKept = 0;
    int durationSkipped = 0;
    int articleDiscarded = 0;
    int videoDiscarded = 0;
    for (ResearchCandidate candidate : candidates) {
      if (candidate == null) {
        continue;
      }
      boolean video = ResearchContentClassifier.isVideo(candidate.getUrl(), candidate.getTitle());
      if (video) {
        if (videoDurationRejector != null) {
          String durationReason = videoDurationRejector.apply(candidate.getUrl());
          if (StrUtil.isNotBlank(durationReason)) {
            durationSkipped++;
            log.info(
                "资料补充跳过超长视频 title={} reason={} url={}",
                candidate.getTitle(),
                durationReason,
                candidate.getUrl());
            continue;
          }
        }
        if (videoKept >= HotNewsResearchPoolLimits.MAX_VIDEOS) {
          videoDiscarded++;
          continue;
        }
        videoKept++;
      } else {
        if (articleKept >= HotNewsResearchPoolLimits.MAX_ARTICLES) {
          articleDiscarded++;
          continue;
        }
        articleKept++;
      }
      kept.add(candidate);
    }
    if (durationSkipped > 0 || articleDiscarded > 0 || videoDiscarded > 0) {
      log.info(
          "资料补充配额裁剪 articleKept={}/{} videoKept={}/{} durationSkipped={} articleDiscarded={} videoDiscarded={}",
          articleKept,
          HotNewsResearchPoolLimits.MAX_ARTICLES,
          videoKept,
          HotNewsResearchPoolLimits.MAX_VIDEOS,
          durationSkipped,
          articleDiscarded,
          videoDiscarded);
    }
    return kept;
  }

  /**
   * 仅剔除超长视频，不做条数裁剪（供 AI 核验前先过滤，避免对超长视频做核验）。
   *
   * @param candidates            候选列表
   * @param videoDurationRejector 视频 URL → 弃用原因；可为 null
   * @return 过滤后的列表
   */
  public static List<ResearchCandidate> rejectOverlongVideos(
      List<ResearchCandidate> candidates,
      Function<String, String> videoDurationRejector) {
    if (CollUtil.isEmpty(candidates) || videoDurationRejector == null) {
      return candidates == null ? List.of() : List.copyOf(candidates);
    }
    List<ResearchCandidate> kept = new ArrayList<>();
    int skipped = 0;
    for (ResearchCandidate candidate : candidates) {
      if (candidate == null) {
        continue;
      }
      if (ResearchContentClassifier.isVideo(candidate.getUrl(), candidate.getTitle())) {
        String durationReason = videoDurationRejector.apply(candidate.getUrl());
        if (StrUtil.isNotBlank(durationReason)) {
          skipped++;
          log.info(
              "资料补充核验前跳过超长视频 title={} reason={} url={}",
              candidate.getTitle(),
              durationReason,
              candidate.getUrl());
          continue;
        }
      }
      kept.add(candidate);
    }
    if (skipped > 0) {
      log.info("资料补充超长视频预过滤 skipped={} kept={}", skipped, kept.size());
    }
    return kept;
  }
}
