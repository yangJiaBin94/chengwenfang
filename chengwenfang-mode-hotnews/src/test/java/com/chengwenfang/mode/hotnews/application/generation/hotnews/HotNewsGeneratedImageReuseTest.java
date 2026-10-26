package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.content.facade.dto.view.GeneratedImageView;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 热点配图复用挑选规则测试。
 */
class HotNewsGeneratedImageReuseTest {

  @Test
  void prefersSameJobOverNewerOtherJob() {
    GeneratedImageView olderSameJob = GeneratedImageView.builder()
        .id("cover-same")
        .kind("cover")
        .sequenceNo(0)
        .jobId("job-a")
        .build();
    GeneratedImageView newerOtherJob = GeneratedImageView.builder()
        .id("cover-other")
        .kind("cover")
        .sequenceNo(0)
        .jobId("job-b")
        .build();
    // 同序号下越新越靠前
    GeneratedImageView picked = HotNewsGeneratedImageReuse.pickCandidate(
        List.of(newerOtherJob, olderSameJob),
        "job-a",
        HotNewsImageKind.COVER,
        0);
    assertThat(picked.getId()).isEqualTo("cover-same");
  }

  @Test
  void doesNotReuseOtherJobImagesOnFreshRun() {
    GeneratedImageView newer = GeneratedImageView.builder()
        .id("inline-new")
        .kind("inline")
        .sequenceNo(2)
        .jobId("job-old")
        .build();
    GeneratedImageView older = GeneratedImageView.builder()
        .id("inline-old")
        .kind("inline")
        .sequenceNo(2)
        .jobId("job-older")
        .build();
    // 新开「生成热点文章」是新 Job：不得回退到历史 Job 配图
    assertThat(HotNewsGeneratedImageReuse.pickCandidate(
        List.of(newer, older),
        "job-fresh",
        HotNewsImageKind.INLINE,
        2)).isNull();
  }

  @Test
  void returnsNullWhenJobIdBlank() {
    GeneratedImageView cover = GeneratedImageView.builder()
        .id("cover-1")
        .kind("cover")
        .sequenceNo(0)
        .jobId("job-a")
        .build();
    assertThat(HotNewsGeneratedImageReuse.pickCandidate(
        List.of(cover), "", HotNewsImageKind.COVER, 0)).isNull();
  }

  @Test
  void returnsNullWhenKindOrSequenceMissing() {
    GeneratedImageView cover = GeneratedImageView.builder()
        .id("cover-1")
        .kind("cover")
        .sequenceNo(0)
        .jobId("job-a")
        .build();
    assertThat(HotNewsGeneratedImageReuse.pickCandidate(
        List.of(cover), "job-a", HotNewsImageKind.INLINE, 1)).isNull();
    assertThat(HotNewsGeneratedImageReuse.pickCandidate(
        List.of(cover), "job-a", HotNewsImageKind.COVER, 1)).isNull();
  }
}
