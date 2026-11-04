package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.crawler.facade.ArticleContentFacade;
import com.chengwenfang.crawler.facade.ArticleTextQuality;
import com.chengwenfang.crawler.facade.PageScreenshotFacade;
import com.chengwenfang.crawler.facade.dto.request.ArticleContentRequest;
import com.chengwenfang.crawler.facade.dto.view.ArticleContentResult;
import com.chengwenfang.crawler.facade.dto.view.PageScreenshotResult;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.application.prompt.ResearchArticleBriefPrompts;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资料补充文章候选：正文瀑布抽取 + AI 事实摘要；失败时截图 Vision 兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchArticleEnricher {

  /** 送入模型的正文上限（对齐雷达事实速览量级）。 */
  static final int BODY_PROMPT_MAX_CHARS = 12_000;
  /** AI 失败时截取正文作摘要的长度。 */
  static final int FALLBACK_BODY_CHARS = 400;
  /** 文本摘要超时秒数。 */
  private static final int BODY_BRIEF_TIMEOUT_SECONDS = 90;
  /** 批量 enrich 并行度。 */
  private static final int PARALLELISM = 3;

  /** 文章正文门面。 */
  private final ArticleContentFacade articleContent;
  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** 网页分段截图门面。 */
  private final PageScreenshotFacade pageScreenshot;
  /** 截图 Vision 摘要。 */
  private final ResearchArticleVisionSummarizer visionSummarizer;

  /**
   * 就地丰富文章候选摘要；视频与空 URL 跳过。
   *
   * @param candidates 候选列表
   */
  public void enrichArticles(List<ResearchCandidate> candidates) {
    if (CollUtil.isEmpty(candidates)) {
      return;
    }
    List<ResearchCandidate> articles = new ArrayList<>();
    for (ResearchCandidate candidate : candidates) {
      if (candidate == null || StrUtil.isBlank(candidate.getUrl())) {
        continue;
      }
      if (ResearchContentClassifier.isVideo(candidate.getUrl(), candidate.getTitle())) {
        continue;
      }
      articles.add(candidate);
    }
    if (articles.isEmpty()) {
      return;
    }
    if (articles.size() == 1) {
      enrichOne(articles.get(0));
      return;
    }
    ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLELISM, articles.size()));
    try {
      List<CompletableFuture<Void>> futures = new ArrayList<>(articles.size());
      for (ResearchCandidate article : articles) {
        futures.add(CompletableFuture.runAsync(() -> enrichOne(article), pool));
      }
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    } finally {
      pool.shutdown();
      try {
        if (!pool.awaitTermination(2, TimeUnit.MINUTES)) {
          pool.shutdownNow();
        }
      } catch (InterruptedException interrupted) {
        pool.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * 丰富单条文章：正文抽取 → AI 摘要；失败或仍导航噪声则截图 Vision。
   *
   * @param candidate 候选
   */
  void enrichOne(ResearchCandidate candidate) {
    String url = candidate.getUrl();
    String priorSummary = StrUtil.nullToEmpty(candidate.getSummary());
    try {
      // 先走正文瀑布；可用且非导航则结束
      ArticleContentResult content = articleContent.resolve(ArticleContentRequest.builder()
          .url(url)
          .title(candidate.getTitle())
          .fallbackSummary(priorSummary)
          .build());
      boolean extracted =
          content != null && content.isUsable() && StrUtil.isNotBlank(content.getText());
      if (extracted) {
        String brief = summarizeBody(candidate.getTitle(), content.getText());
        if (StrUtil.isBlank(brief)) {
          brief = StrUtil.sub(content.getText().trim(), 0, FALLBACK_BODY_CHARS);
        }
        String simplified = ChineseTextNormalizer.toSimplified(brief.trim());
        // 导航噪声或「信息不足」占位句：不入库，走截图 Vision
        if (!ArticleTextQuality.looksLikeNavigationNoise(simplified)
            && !ArticleTextQuality.looksLikeInsufficientBrief(simplified)) {
          candidate.setSummary(simplified);
          log.info(
              "资料补充文章摘要已 enrich source={} title={} url={}",
              content.getSource() == null ? "?" : content.getSource().getValue(),
              candidate.getTitle(),
              url);
          return;
        }
        log.warn(
            "资料补充正文摘要不可用（导航或信息不足），尝试截图 Vision 兜底 title={} url={}",
            candidate.getTitle(),
            url);
      } else {
        log.warn(
            "资料补充正文抽取失败，尝试截图 Vision 兜底 title={} url={} err={}",
            candidate.getTitle(),
            url,
            content == null ? "null" : content.getErrorMessage());
      }
      if (!tryVisionScreenshotFallback(candidate)) {
        log.info(
            "vision_fallback_skipped_or_failed title={} url={} keptPrior={}",
            candidate.getTitle(),
            url,
            StrUtil.isNotBlank(priorSummary));
      }
    } catch (Exception exception) {
      log.warn(
          "资料补充文章 enrich 异常 title={} url={} err={}",
          candidate.getTitle(),
          url,
          exception.getMessage());
      tryVisionScreenshotFallback(candidate);
    }
  }

  /**
   * 截图 + Vision 写摘要；成功返回 true。
   *
   * @param candidate 候选
   * @return 是否写入新摘要
   */
  boolean tryVisionScreenshotFallback(ResearchCandidate candidate) {
    if (!pageScreenshot.isEnabled()) {
      log.warn("vision_fallback_skipped reason=page_screenshot_disabled url={}", candidate.getUrl());
      return false;
    }
    // 分段截图；用完即删临时 PNG
    PageScreenshotResult shot = pageScreenshot.capture(candidate.getUrl());
    List<String> paths = shot == null || shot.getPaths() == null ? List.of() : shot.getPaths();
    try {
      if (shot == null || !shot.isOk() || paths.isEmpty()) {
        log.warn(
            "vision_fallback_failed reason=screenshot url={} err={}",
            candidate.getUrl(),
            shot == null ? "null" : shot.getError());
        return false;
      }
      String brief = visionSummarizer.summarize(candidate.getTitle(), paths);
      if (StrUtil.isBlank(brief)
          || ArticleTextQuality.looksLikeNavigationNoise(brief)
          || ArticleTextQuality.looksLikeInsufficientBrief(brief)) {
        log.warn("vision_fallback_failed reason=empty_or_nav url={}", candidate.getUrl());
        return false;
      }
      candidate.setSummary(brief);
      log.info("资料补充文章摘要已 Vision 兜底 title={} url={}", candidate.getTitle(), candidate.getUrl());
      return true;
    } finally {
      pageScreenshot.cleanup(paths);
    }
  }

  /**
   * 将正文压成事实摘要；AI 失败返回空串由调用方截正文。
   *
   * @param title 标题
   * @param body  正文
   * @return 摘要；失败空串
   */
  private String summarizeBody(String title, String body) {
    String clipped = StrUtil.sub(StrUtil.nullToEmpty(body), 0, BODY_PROMPT_MAX_CHARS);
    if (StrUtil.isBlank(clipped)) {
      return "";
    }
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_TOPIC_RESEARCH_ARTICLE_BRIEF)
          .systemPrompt(ResearchArticleBriefPrompts.SYSTEM)
          .prompt("标题：" + StrUtil.nullToEmpty(title) + "\n\n正文：\n" + clipped)
          .timeoutSeconds(BODY_BRIEF_TIMEOUT_SECONDS)
          .build());
      String text = result == null ? "" : StrUtil.trim(result.getText());
      return StrUtil.nullToEmpty(text);
    } catch (Exception exception) {
      log.warn("资料补充文章 AI 摘要失败 title={} err={}", title, exception.getMessage());
      return "";
    }
  }

  /**
   * 摘要是否像站点导航/菜单噪声（委托爬虫质量工具，供单测）。
   *
   * @param summary 摘要
   * @return 疑似导航时 true
   */
  static boolean looksLikeNavigationNoise(String summary) {
    return ArticleTextQuality.looksLikeNavigationNoise(summary);
  }
}
