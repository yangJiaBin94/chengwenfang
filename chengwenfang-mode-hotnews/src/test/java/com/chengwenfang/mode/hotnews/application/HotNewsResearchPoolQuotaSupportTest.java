package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsResearchPoolLimits;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchItemKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** {@link HotNewsResearchPoolQuotaSupport} 时长过滤与条数上限。 */
class HotNewsResearchPoolQuotaSupportTest {

  @Test
  void reject_overlong_videos_keeps_articles_and_short_videos() {
    List<ResearchCandidate> input = List.of(
        article("文章甲", "https://example.com/a1"),
        video("超长视频", "https://www.youtube.com/watch?v=long"),
        video("短视频", "https://www.youtube.com/watch?v=short"));

    Function<String, String> rejector = url ->
        url != null && url.contains("long") ? "视频时长超过上限" : null;

    List<ResearchCandidate> kept =
        HotNewsResearchPoolQuotaSupport.rejectOverlongVideos(input, rejector);

    assertThat(kept).extracting(ResearchCandidate::getTitle)
        .containsExactly("文章甲", "短视频");
  }

  @Test
  void apply_limits_caps_article_and_video_separately() {
    List<ResearchCandidate> input = new ArrayList<>();
    for (int index = 1; index <= HotNewsResearchPoolLimits.MAX_ARTICLES + 3; index++) {
      input.add(article("文章" + index, "https://example.com/a" + index));
    }
    for (int index = 1; index <= HotNewsResearchPoolLimits.MAX_VIDEOS + 2; index++) {
      input.add(video("视频" + index, "https://www.youtube.com/watch?v=v" + index));
    }

    List<ResearchCandidate> kept = HotNewsResearchPoolQuotaSupport.applyLimits(input, null);

    long articles = kept.stream()
        .filter(item -> item.getUrl().contains("/a"))
        .count();
    long videos = kept.stream()
        .filter(item -> item.getUrl().contains("youtube"))
        .count();
    assertThat(articles).isEqualTo(HotNewsResearchPoolLimits.MAX_ARTICLES);
    assertThat(videos).isEqualTo(HotNewsResearchPoolLimits.MAX_VIDEOS);
    assertThat(kept.get(0).getTitle()).isEqualTo("文章1");
    assertThat(kept).extracting(ResearchCandidate::getTitle)
        .contains("视频1", "视频" + HotNewsResearchPoolLimits.MAX_VIDEOS)
        .doesNotContain("文章" + (HotNewsResearchPoolLimits.MAX_ARTICLES + 1))
        .doesNotContain("视频" + (HotNewsResearchPoolLimits.MAX_VIDEOS + 1));
  }

  @Test
  void apply_limits_skips_overlong_before_counting_quota() {
    List<ResearchCandidate> input = List.of(
        video("超长1", "https://www.youtube.com/watch?v=long1"),
        video("短1", "https://www.youtube.com/watch?v=short1"),
        video("超长2", "https://www.youtube.com/watch?v=long2"));

    Function<String, String> rejector = url ->
        url != null && url.contains("long") ? "超时" : null;

    List<ResearchCandidate> kept =
        HotNewsResearchPoolQuotaSupport.applyLimits(input, rejector);

    assertThat(kept).extracting(ResearchCandidate::getTitle).containsExactly("短1");
  }

  private static ResearchCandidate article(String title, String url) {
    return ResearchCandidate.builder()
        .title(title)
        .url(url)
        .summary("")
        .score(80)
        .kind(ResearchItemKind.WEB)
        .build();
  }

  private static ResearchCandidate video(String title, String url) {
    return ResearchCandidate.builder()
        .title(title)
        .url(url)
        .summary("")
        .score(80)
        .kind(ResearchItemKind.WEB)
        .build();
  }
}
