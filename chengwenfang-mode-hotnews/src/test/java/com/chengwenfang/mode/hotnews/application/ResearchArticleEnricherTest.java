package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.crawler.facade.ArticleContentFacade;
import com.chengwenfang.crawler.facade.PageScreenshotFacade;
import com.chengwenfang.crawler.facade.dto.request.ArticleContentRequest;
import com.chengwenfang.crawler.facade.dto.view.ArticleContentResult;
import com.chengwenfang.crawler.facade.dto.view.PageScreenshotResult;
import com.chengwenfang.crawler.facade.enums.ArticleContentSource;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchItemKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ResearchArticleEnricher} 正文摘要、导航噪声与截图 Vision 兜底。 */
@ExtendWith(MockitoExtension.class)
class ResearchArticleEnricherTest {

  @Mock
  private ArticleContentFacade articleContent;
  @Mock
  private AiTextFacade ai;
  @Mock
  private PageScreenshotFacade pageScreenshot;
  @Mock
  private ResearchArticleVisionSummarizer visionSummarizer;

  @InjectMocks
  private ResearchArticleEnricher enricher;

  @Test
  void enrich_replaces_summary_when_body_usable() {
    when(articleContent.resolve(any(ArticleContentRequest.class))).thenReturn(
        ArticleContentResult.builder()
            .usable(true)
            .text("11月3日，皇冠新材上市首日高开，截至午前涨幅一度超过250%，发行价16.98元。")
            .source(ArticleContentSource.JINA)
            .build());
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("皇冠新材上市首日大涨，午前涨幅超250%，发行价16.98元。")
        .build());

    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("新股开盘")
        .url("https://finance.eastmoney.com/a/202611033123.html")
        .summary("[财经](http://x) * [焦点](http://y) * [股票](http://z)")
        .kind(ResearchItemKind.WEB)
        .build();

    enricher.enrichArticles(List.of(candidate));

    assertThat(candidate.getSummary()).contains("皇冠新材");
    assertThat(candidate.getSummary()).doesNotContain("[财经]");
    verify(pageScreenshot, never()).capture(any());
  }

  @Test
  void enrich_keeps_prior_summary_when_body_unusable_and_screenshot_off() {
    when(pageScreenshot.isEnabled()).thenReturn(false);
    when(articleContent.resolve(any(ArticleContentRequest.class))).thenReturn(
        ArticleContentResult.builder()
            .usable(false)
            .text("")
            .errorMessage("empty")
            .source(ArticleContentSource.NONE)
            .build());

    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("保留摘要")
        .url("https://example.com/a")
        .summary("原有搜索摘要仍可用")
        .kind(ResearchItemKind.WEB)
        .build();

    enricher.enrichArticles(List.of(candidate));

    assertThat(candidate.getSummary()).isEqualTo("原有搜索摘要仍可用");
    verify(ai, never()).completeText(any());
    verify(pageScreenshot, never()).capture(any());
  }

  @Test
  void enrich_vision_fallback_when_extract_fails() {
    when(pageScreenshot.isEnabled()).thenReturn(true);
    when(articleContent.resolve(any(ArticleContentRequest.class))).thenReturn(
        ArticleContentResult.builder()
            .usable(false)
            .text("")
            .errorMessage("empty")
            .source(ArticleContentSource.NONE)
            .build());
    when(pageScreenshot.capture("https://example.com/fail")).thenReturn(
        PageScreenshotResult.builder()
            .ok(true)
            .paths(List.of("C:/tmp/shot1.png"))
            .build());
    when(visionSummarizer.summarize(eq("抽失败"), anyList())).thenReturn(
        "开盘涨跌：上证指数跌0.5%，创业板涨1.2%。");

    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("抽失败")
        .url("https://example.com/fail")
        .summary("原搜索摘要")
        .kind(ResearchItemKind.WEB)
        .build();

    enricher.enrichArticles(List.of(candidate));

    assertThat(candidate.getSummary()).contains("上证指数");
    verify(visionSummarizer).summarize(eq("抽失败"), anyList());
    verify(pageScreenshot).cleanup(anyList());
  }

  @Test
  void enrich_vision_fallback_when_brief_says_insufficient() {
    when(pageScreenshot.isEnabled()).thenReturn(true);
    when(articleContent.resolve(any(ArticleContentRequest.class))).thenReturn(
        ArticleContentResult.builder()
            .usable(true)
            .text("* [财经](http://a.com) * [焦点](http://b.com) * [股票](http://c.com) 以及一堆菜单")
            .source(ArticleContentSource.JINA)
            .build());
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("信息不足，无法形成可靠摘要（正文仅为网站导航链接，除标题外无可核对的具体事实）。")
        .build());
    when(pageScreenshot.capture("https://finance.eastmoney.com/a/x.html")).thenReturn(
        PageScreenshotResult.builder().ok(true).paths(List.of("C:/tmp/e.png")).build());
    when(visionSummarizer.summarize(eq("东方财富"), anyList())).thenReturn(
        "11月3日皇冠新材上市首日开盘大涨，最高涨288.69%，报60.01元，发行价16.98元。");

    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("东方财富")
        .url("https://finance.eastmoney.com/a/x.html")
        .summary("浅抓")
        .kind(ResearchItemKind.WEB)
        .build();

    enricher.enrichArticles(List.of(candidate));

    assertThat(candidate.getSummary()).contains("288.69");
    verify(pageScreenshot).capture(any());
  }

  @Test
  void enrich_skips_screenshot_when_body_usable_and_not_nav() {
    when(articleContent.resolve(any(ArticleContentRequest.class))).thenReturn(
        ArticleContentResult.builder()
            .usable(true)
            .text("某公司发布三季报，营收同比增长12%。")
            .source(ArticleContentSource.TRAFILATURA)
            .build());
    when(ai.completeText(any(TextCompleteRequest.class))).thenReturn(TextCompleteResult.builder()
        .text("该公司三季报营收同比增长12%。")
        .build());

    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("财报")
        .url("https://example.com/ok")
        .summary("浅抓摘要")
        .kind(ResearchItemKind.WEB)
        .build();

    enricher.enrichArticles(List.of(candidate));

    assertThat(candidate.getSummary()).contains("12%");
    verify(pageScreenshot, never()).capture(any());
    verify(visionSummarizer, never()).summarize(any(), anyList());
  }

  @Test
  void enrich_skips_video_without_resolve() {
    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("短视频")
        .url("https://www.youtube.com/watch?v=abc")
        .summary("口播摘要")
        .kind(ResearchItemKind.WEB)
        .build();

    enricher.enrichArticles(List.of(candidate));

    verify(articleContent, never()).resolve(any());
    assertThat(candidate.getSummary()).isEqualTo("口播摘要");
  }

  @Test
  void looks_like_navigation_noise_detects_markdown_menu() {
    assertThat(ResearchArticleEnricher.looksLikeNavigationNoise(
        "[财经](http://a.com) * [焦点](http://b.com) * [股票](http://c.com)"))
        .isTrue();
    assertThat(ResearchArticleEnricher.looksLikeNavigationNoise(
        "皇冠新材上市首日大涨，涨幅超过百分之二百。"))
        .isFalse();
  }
}
