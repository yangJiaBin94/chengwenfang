package com.workbench.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.workbench.sourcing.facade.enums.SourceMediaType;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * 雷达采集素材配额：有发布时间优先，再分别裁剪文章与视频上限。
 */
@Slf4j
public final class RadarCollectMediaQuotaSupport {
  private RadarCollectMediaQuotaSupport() {}

  /**
   * 先按「有发布时间优先、新到旧」重排未弃用候选，再按文章/视频配额裁剪超额项。
   *
   * @param session 采集会话
   * @return 裁剪结果（文章/视频各自超额数）
   */
  public static CapResult applyCap(RadarCollectSessionStore.Session session) {
    return applyCap(session, null);
  }

  /**
   * 先按「有发布时间优先、新到旧」重排；视频可先按时长拒绝，再按文章/视频配额裁剪。
   * <p>时长拒绝不占用视频配额名额，便于用后续候选补齐。
   *
   * @param session              采集会话
   * @param videoDurationRejector 视频 URL → 弃用原因；返回空表示可保留；可为 null
   * @return 裁剪结果（文章/视频各自超额或时长弃用数）
   */
  public static CapResult applyCap(
      RadarCollectSessionStore.Session session,
      Function<String, String> videoDurationRejector) {
    if (session == null || session.getCandidates() == null || session.getCandidates().isEmpty()) {
      return CapResult.EMPTY;
    }
    // 有时间素材排前，同有时间时越新越靠前；无时间整体靠后
    session.getCandidates().sort(publishPriorityComparator());
    int maxArticle = Math.max(0, session.getMaxArticlePerFetch());
    int maxVideo = Math.max(0, session.getMaxVideoPerFetch());
    int articleKept = 0;
    int videoKept = 0;
    int articleDiscarded = 0;
    int videoDiscarded = 0;
    for (RadarCollectCandidate candidate : session.getCandidates()) {
      if (candidate == null || candidate.isDiscarded()) {
        continue;
      }
      boolean video = SourceMediaType.from(candidate.getMediaType()).isVideo();
      if (video) {
        // 超长视频先跳过，不占配额，后续候选可补位
        if (videoDurationRejector != null) {
          String durationReason = videoDurationRejector.apply(candidate.getUrl());
          if (StrUtil.isNotBlank(durationReason)) {
            discard(candidate, durationReason);
            videoDiscarded++;
            continue;
          }
        }
        if (videoKept >= maxVideo) {
          discard(candidate, "超出本轮视频素材配额（上限 " + maxVideo + "）");
          videoDiscarded++;
        } else {
          videoKept++;
        }
      } else if (articleKept >= maxArticle) {
        discard(candidate, "超出本轮文章素材配额（上限 " + maxArticle + "）");
        articleDiscarded++;
      } else {
        articleKept++;
      }
    }
    if (articleDiscarded > 0 || videoDiscarded > 0) {
      log.info(
          "雷达素材配额裁剪完成 articleKept={}/{} videoKept={}/{} articleDiscarded={} videoDiscarded={}",
          articleKept,
          maxArticle,
          videoKept,
          maxVideo,
          articleDiscarded,
          videoDiscarded);
    }
    return new CapResult(articleDiscarded, videoDiscarded);
  }

  /**
   * @return 有发布时间优先、新到旧的比较器
   */
  static Comparator<RadarCollectCandidate> publishPriorityComparator() {
    return (left, right) -> {
      if (left == null && right == null) {
        return 0;
      }
      if (left == null) {
        return 1;
      }
      if (right == null) {
        return -1;
      }
      LocalDateTime leftPublished = left.getPublishedAt();
      LocalDateTime rightPublished = right.getPublishedAt();
      boolean leftHasTime = leftPublished != null;
      boolean rightHasTime = rightPublished != null;
      if (leftHasTime != rightHasTime) {
        return leftHasTime ? -1 : 1;
      }
      if (leftHasTime) {
        return rightPublished.compareTo(leftPublished);
      }
      return 0;
    };
  }

  /**
   * 标记候选弃用并清空原文。
   *
   * @param candidate 候选
   * @param reason    弃用原因
   */
  private static void discard(RadarCollectCandidate candidate, String reason) {
    candidate.setDiscarded(true);
    candidate.setDiscardReason(reason);
    candidate.setRawText(null);
    log.info("雷达素材配额裁剪 title={} reason={}", candidate.getTitle(), reason);
  }

  /**
   * 配额裁剪计数。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class CapResult {
    /** 因文章上限弃用数。 */
    private final int articleDiscarded;
    /** 因视频上限弃用数。 */
    private final int videoDiscarded;

    /** 空结果。 */
    public static final CapResult EMPTY = new CapResult(0, 0);

    /**
     * @return 合计弃用数
     */
    public int total() {
      return articleDiscarded + videoDiscarded;
    }
  }
}
