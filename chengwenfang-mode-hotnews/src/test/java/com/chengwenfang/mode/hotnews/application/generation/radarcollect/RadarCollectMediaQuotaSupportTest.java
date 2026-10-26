package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.sourcing.facade.enums.SourceMediaType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link RadarCollectMediaQuotaSupport} 有时间优先与文章/视频配额。 */
class RadarCollectMediaQuotaSupportTest {

  @Test
  void prefers_dated_then_caps_article_and_video() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setMaxArticlePerFetch(2);
    session.setMaxVideoPerFetch(1);
    LocalDateTime newer = LocalDateTime.now().minusHours(1);
    LocalDateTime older = LocalDateTime.now().minusHours(5);

    session.getCandidates().add(article("无时间文章甲", null));
    session.getCandidates().add(video("有时间视频旧", older));
    session.getCandidates().add(article("有时间文章新", newer));
    session.getCandidates().add(article("有时间文章旧", older));
    session.getCandidates().add(video("无时间视频", null));
    session.getCandidates().add(article("无时间文章乙", null));

    RadarCollectMediaQuotaSupport.CapResult result =
        RadarCollectMediaQuotaSupport.applyCap(session);

    assertThat(result.articleDiscarded()).isEqualTo(2);
    assertThat(result.videoDiscarded()).isEqualTo(1);
    // 全局按发布时间排序后裁剪：新文章 → 同刻视频/旧文章（稳定序）→ 无时间被配额裁掉
    assertThat(session.survivors()).extracting(RadarCollectCandidate::getTitle)
        .containsExactly("有时间文章新", "有时间视频旧", "有时间文章旧");
  }

  @Test
  void zero_video_quota_keeps_articles_only() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setMaxArticlePerFetch(10);
    session.setMaxVideoPerFetch(0);
    session.getCandidates().add(video("视频一", LocalDateTime.now()));
    session.getCandidates().add(article("文章一", null));

    RadarCollectMediaQuotaSupport.CapResult result =
        RadarCollectMediaQuotaSupport.applyCap(session);

    assertThat(result.videoDiscarded()).isEqualTo(1);
    assertThat(session.survivors()).extracting(RadarCollectCandidate::getTitle)
        .containsExactly("文章一");
  }

  @Test
  void apply_twice_is_idempotent() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setMaxArticlePerFetch(1);
    session.setMaxVideoPerFetch(1);
    session.getCandidates().add(article("文章一", LocalDateTime.now()));
    session.getCandidates().add(article("文章二", null));
    session.getCandidates().add(video("视频一", LocalDateTime.now()));
    session.getCandidates().add(video("视频二", null));

    assertThat(RadarCollectMediaQuotaSupport.applyCap(session).total()).isEqualTo(2);
    assertThat(RadarCollectMediaQuotaSupport.applyCap(session).total()).isEqualTo(0);
    assertThat(session.survivors()).hasSize(2);
  }

  @Test
  void duration_reject_does_not_consume_video_quota() {
    RadarCollectSessionStore.Session session = new RadarCollectSessionStore.Session();
    session.setMaxArticlePerFetch(10);
    session.setMaxVideoPerFetch(1);
    session.getCandidates().add(video("超长视频", LocalDateTime.now().minusHours(1)));
    session.getCandidates().add(video("短视频", LocalDateTime.now().minusHours(2)));

    RadarCollectMediaQuotaSupport.CapResult result =
        RadarCollectMediaQuotaSupport.applyCap(
            session,
            url -> url != null && url.contains("超长视频") ? "视频时长超过上限（125 分钟 > 30 分钟），已跳过" : null);

    assertThat(result.videoDiscarded()).isEqualTo(1);
    assertThat(session.survivors()).extracting(RadarCollectCandidate::getTitle)
        .containsExactly("短视频");
    assertThat(session.getCandidates().stream().filter(RadarCollectCandidate::isDiscarded)
        .map(RadarCollectCandidate::getDiscardReason).findFirst().orElse(""))
        .contains("时长超过上限");
  }

  private static RadarCollectCandidate video(String title, LocalDateTime publishedAt) {
    return RadarCollectCandidate.builder()
        .title(title)
        .url("https://www.bilibili.com/video/" + title)
        .publishedAt(publishedAt)
        .mediaType(SourceMediaType.VIDEO.getValue())
        .build();
  }

  private static RadarCollectCandidate article(String title, LocalDateTime publishedAt) {
    return RadarCollectCandidate.builder()
        .title(title)
        .url("https://example.com/" + title)
        .publishedAt(publishedAt)
        .mediaType(SourceMediaType.ARTICLE.getValue())
        .build();
  }
}
