package com.chengwenfang.mode.hotnews.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResearchContentClassifierTest {
  @Test
  void classifies_video_by_url() {
    assertThat(ResearchContentClassifier.mediaLabel(
        "https://www.youtube.com/watch?v=abc", "Some talk")).isEqualTo("视频");
    assertThat(ResearchContentClassifier.mediaLabel(
        "https://www.bilibili.com/video/BV1xx411c7mD", "讲解")).isEqualTo("视频");
    assertThat(ResearchContentClassifier.mediaLabel(
        "http://www.bilibili.com/video/av117425144072633", "驼王奶粉")).isEqualTo("视频");
    assertThat(ResearchContentClassifier.isVideo(
        "http://www.bilibili.com/video/av117425144072633", "驼王奶粉")).isTrue();
  }

  @Test
  void classifies_article_by_default() {
    assertThat(ResearchContentClassifier.mediaLabel(
        "https://blog.csdn.net/foo/bar", "Reflection AI 发布 Beam")).isEqualTo("文章");
  }

  @Test
  void arxiv_with_video_in_title_is_article() {
    assertThat(ResearchContentClassifier.isVideo(
        "https://arxiv.org/abs/2405.16381",
        "StreamPro: Reinforcement Learning with Multi-Grained Rewards for Proactive Streaming Video Understanding"))
        .isFalse();
    assertThat(ResearchContentClassifier.mediaLabel(
        "https://arxiv.org/abs/2405.16381",
        "StreamPro: Streaming Video Understanding")).isEqualTo("文章");
  }

  @Test
  void title_keyword_fallback_only_without_url() {
    assertThat(ResearchContentClassifier.isVideo(null, "本周抖音热门")).isTrue();
    assertThat(ResearchContentClassifier.isVideo("", "短视频合集")).isTrue();
    assertThat(ResearchContentClassifier.isVideo(
        "https://example.org/post", "本周抖音热门")).isFalse();
  }

  @Test
  void resolves_site_label_from_url() {
    assertThat(ResearchContentClassifier.siteLabel(
        "https://www.bilibili.com/video/BV1xx411c7mD")).isEqualTo("B站");
    assertThat(ResearchContentClassifier.siteLabel(
        "http://www.bilibili.com/video/av117425144072633")).isEqualTo("B站");
    assertThat(ResearchContentClassifier.siteLabel(
        "https://youtu.be/abc123")).isEqualTo("YouTube");
    assertThat(ResearchContentClassifier.siteLabel(
        "https://www.zhihu.com/question/1")).isEqualTo("知乎");
    assertThat(ResearchContentClassifier.siteLabel(
        "https://blog.csdn.net/foo/bar")).isEqualTo("CSDN");
    assertThat(ResearchContentClassifier.siteLabel(
        "https://arxiv.org/abs/1")).isEqualTo("arXiv");
    assertThat(ResearchContentClassifier.siteLabel(
        "https://example.org/a")).isEqualTo("example.org");
    assertThat(ResearchContentClassifier.siteLabel(""))
        .isEqualTo(ResearchContentClassifier.UNKNOWN_SITE_LABEL);
    assertThat(ResearchContentClassifier.isKnownSite("B站")).isTrue();
    assertThat(ResearchContentClassifier.isKnownSite(
        ResearchContentClassifier.UNKNOWN_SITE_LABEL)).isFalse();
  }

  @Test
  void detects_preprint_hosts_and_label_fallback() {
    assertThat(ResearchContentClassifier.isPreprintOrPaperRepo(
        "https://www.biorxiv.org/content/10.1101/x", null, null)).isTrue();
    assertThat(ResearchContentClassifier.isPreprintOrPaperRepo(
        "https://openreview.net/forum?id=1", null, null)).isTrue();
    assertThat(ResearchContentClassifier.isPreprintOrPaperRepo(
        "https://techcrunch.com/a", null, null)).isFalse();
    assertThat(ResearchContentClassifier.isPreprintOrPaperRepo(
        "", "SSRN", "working paper")).isTrue();
  }

  @Test
  void detects_non_chinese_titles() {
    assertThat(ResearchContentClassifier.needsChineseTranslation(
        "NVIDIA Cultivating Its Own Rival? An $800 Million Bet")).isTrue();
    assertThat(ResearchContentClassifier.needsChineseTranslation(
        "BeamのMoEって何？必要な「専門家」だけが働くAIの仕組み")).isTrue();
    assertThat(ResearchContentClassifier.needsChineseTranslation(
        "Reflection AI 发布开放权重模型 Beam")).isFalse();
  }
}
