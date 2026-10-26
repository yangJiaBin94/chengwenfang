package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.crawler.facade.ArticleContentFacade;
import com.chengwenfang.crawler.facade.PublishTimeUtil;
import com.chengwenfang.crawler.facade.dto.request.ArticleContentRequest;
import com.chengwenfang.crawler.facade.dto.view.ArticleContentResult;
import com.chengwenfang.crawler.facade.enums.ArticleContentSource;
import com.chengwenfang.mode.hotnews.facade.VideoParseSink;
import com.chengwenfang.mode.hotnews.facade.VideoTranscriptFacade;
import com.chengwenfang.sourcing.facade.enums.SourceMediaType;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 雷达采集解析：文章抓正文与发布日、视频同步字幕/ASR（临时目录，原文不落库）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RadarCollectEnrichSupport {
  /** 视频转录门面。 */
  private final VideoTranscriptFacade videoTranscript;
  /** AI 速览与发布日。 */
  private final RadarCollectAiSupport aiSupport;
  /** 文章正文门面（含原始 HTML 日期补爬）。 */
  private final ArticleContentFacade articleContent;

  /**
   * 丰富单条候选：打标后的解析与速览；失败则标记 discarded。
   *
   * @param candidate     候选
   * @param skillBindings 节点 Skill
   * @param allowVideo    是否允许本轮占用视频配额
   * @return true 表示消耗了一次视频配额
   */
  public boolean enrichOne(
      RadarCollectCandidate candidate,
      List<SkillPromptBindingView> skillBindings,
      boolean allowVideo) {
    if (candidate == null || candidate.isDiscarded()) {
      return false;
    }
    boolean video = SourceMediaType.from(candidate.getMediaType()).isVideo();
    if (video) {
      if (!allowVideo) {
        discard(candidate, "视频配额未处理");
        return false;
      }
      return enrichVideo(candidate, skillBindings);
    }
    enrichArticle(candidate, skillBindings);
    return false;
  }

  /**
   * 文章：正文瀑布 + 发布日补全 + 事实速览。
   *
   * @param candidate     候选
   * @param skillBindings Skill
   */
  private void enrichArticle(
      RadarCollectCandidate candidate, List<SkillPromptBindingView> skillBindings) {
    ArticleContentResult content = articleContent.resolve(ArticleContentRequest.builder()
        .url(candidate.getUrl())
        .fallbackSummary(candidate.getFetchSummary())
        .title(candidate.getTitle())
        .build());
    ArticleContentSource source = content == null || content.getSource() == null
        ? ArticleContentSource.NONE
        : content.getSource();
    if (content == null || !content.isUsable() || StrUtil.isBlank(content.getText())) {
      applyPublishDateSource(candidate, ArticlePublishDateSource.NO_CONTENT);
      log.info(
          "雷达文章发布日跳过 dateSource={} contentSource={} reason=no_usable_content title={} url={}",
          ArticlePublishDateSource.NO_CONTENT.getValue(),
          source.getValue(),
          candidate.getTitle(),
          candidate.getUrl());
      discard(candidate, "文章无可用正文（来源=" + source.getValue() + "）");
      return;
    }
    candidate.setRawText(content.getText());
    // 仅在原发布时间缺失时走正则+AI（及 Jina 后再爬 HTML / metadata 回退）
    if (candidate.getPublishedAt() != null) {
      applyPublishDateSource(candidate, ArticlePublishDateSource.CHANNEL);
      log.info(
          "雷达文章发布日命中 dateSource={} publishedAt={} title={} url={}",
          ArticlePublishDateSource.CHANNEL.getValue(),
          candidate.getPublishedAt(),
          candidate.getTitle(),
          candidate.getUrl());
    } else {
      fillPublishDate(candidate, skillBindings, content);
    }
    ArticlePublishDateSource dateSource = ArticlePublishDateSource.from(
        candidate.getPublishDateSource());
    log.info(
        "雷达文章正文已接入 contentSource={} costMs={} publishedAt={} dateSource={} dateLabel={} title={} url={}",
        source.getValue(),
        content.getCostMs(),
        candidate.getPublishedAt(),
        dateSource.getValue(),
        dateSource.getLabel(),
        candidate.getTitle(),
        candidate.getUrl());
    String brief = aiSupport.articleFactBrief(skillBindings, candidate.getTitle(), content.getText());
    if (StrUtil.isBlank(brief) || brief.contains("信息不足")) {
      discard(candidate, "文章事实速览失败");
      return;
    }
    candidate.setFactBrief(brief.trim());
  }

  /**
   * 发布日：Pass1 按来源文本 → AI；Jina 抽空则 Pass2 再爬 HTML；仍空回退 metadata.date。
   *
   * @param candidate     候选
   * @param skillBindings Skill
   * @param content       正文结果
   */
  private void fillPublishDate(
      RadarCollectCandidate candidate,
      List<SkillPromptBindingView> skillBindings,
      ArticleContentResult content) {
    ArticleContentSource source = content.getSource() == null
        ? ArticleContentSource.NONE
        : content.getSource();
    String dateInput = content.getDateInputText();
    // Pass1：正文侧 dateInputText → AI
    LocalDateTime published = null;
    if (StrUtil.isNotBlank(dateInput)) {
      published = aiSupport.extractPublishDate(
          skillBindings, candidate.getTitle(), dateInput);
      if (published != null) {
        candidate.setPublishedAt(published);
        applyPublishDateSource(candidate, ArticlePublishDateSource.AI_CONTENT);
        log.info(
            "雷达文章发布日命中 dateSource={} contentSource={} publishedAt={} title={} url={}",
            ArticlePublishDateSource.AI_CONTENT.getValue(),
            source.getValue(),
            published,
            candidate.getTitle(),
            candidate.getUrl());
        return;
      }
      log.info(
          "雷达文章发布日未命中 pass=ai_content contentSource={} dateInputChars={} title={} url={}",
          source.getValue(),
          dateInput.length(),
          candidate.getTitle(),
          candidate.getUrl());
    } else {
      log.info(
          "雷达文章发布日跳过 pass=ai_content reason=empty_date_input contentSource={} title={} url={}",
          source.getValue(),
          candidate.getTitle(),
          candidate.getUrl());
    }
    // Pass2：Jina 抽空时再爬 HTML → AI
    if (source == ArticleContentSource.JINA) {
      String htmlDateInput = articleContent.fetchRawHtmlDateInputText(candidate.getUrl());
      if (StrUtil.isNotBlank(htmlDateInput)) {
        published = aiSupport.extractPublishDate(
            skillBindings, candidate.getTitle(), htmlDateInput);
        if (published != null) {
          candidate.setPublishedAt(published);
          applyPublishDateSource(candidate, ArticlePublishDateSource.AI_HTML);
          log.info(
              "雷达文章发布日命中 dateSource={} contentSource={} htmlChars={} publishedAt={} title={} url={}",
              ArticlePublishDateSource.AI_HTML.getValue(),
              source.getValue(),
              htmlDateInput.length(),
              published,
              candidate.getTitle(),
              candidate.getUrl());
          return;
        }
        log.info(
            "雷达文章发布日未命中 pass=ai_html contentSource={} htmlChars={} title={} url={}",
            source.getValue(),
            htmlDateInput.length(),
            candidate.getTitle(),
            candidate.getUrl());
      } else {
        log.info(
            "雷达文章发布日跳过 pass=ai_html reason=empty_html contentSource={} title={} url={}",
            source.getValue(),
            candidate.getTitle(),
            candidate.getUrl());
      }
    }
    // 回退：Trafilatura metadata.date
    if (StrUtil.isNotBlank(content.getMetadataDate())) {
      published = PublishTimeUtil.parseFlexibleDate(content.getMetadataDate());
      if (published != null) {
        candidate.setPublishedAt(published);
        applyPublishDateSource(candidate, ArticlePublishDateSource.METADATA);
        log.info(
            "雷达文章发布日命中 dateSource={} metadataDate={} publishedAt={} title={} url={}",
            ArticlePublishDateSource.METADATA.getValue(),
            content.getMetadataDate(),
            published,
            candidate.getTitle(),
            candidate.getUrl());
        return;
      }
      log.info(
          "雷达文章发布日未命中 pass=metadata metadataDate={} title={} url={}",
          content.getMetadataDate(),
          candidate.getTitle(),
          candidate.getUrl());
    }
    applyPublishDateSource(candidate, ArticlePublishDateSource.NONE);
    log.info(
        "雷达文章发布日未抽到 dateSource={} contentSource={} title={} url={}",
        ArticlePublishDateSource.NONE.getValue(),
        source.getValue(),
        candidate.getTitle(),
        candidate.getUrl());
  }

  /**
   * 写入发布日来源键。
   *
   * @param candidate 候选
   * @param source    来源
   */
  private static void applyPublishDateSource(
      RadarCollectCandidate candidate, ArticlePublishDateSource source) {
    if (candidate == null || source == null) {
      return;
    }
    candidate.setPublishDateSource(source.getValue());
  }

  private boolean enrichVideo(
      RadarCollectCandidate candidate, List<SkillPromptBindingView> skillBindings) {
    InMemoryVideoSink sink = new InMemoryVideoSink(candidate);
    try {
      videoTranscript.parseSink(sink, VideoParseStep.FETCH_SUBTITLE, (step, msg) -> { });
    } catch (Exception exception) {
      discard(candidate, "视频解析异常：" + StrUtil.sub(exception.getMessage(), 0, 120));
      return true;
    }
    if (!VideoParseStatus.from(sink.parseStatus.get()).isSucceeded()) {
      discard(candidate, StrUtil.blankToDefault(sink.parseError.get(), "视频解析失败"));
      return true;
    }
    String raw = StrUtil.blankToDefault(sink.rawText.get(), sink.summary.get());
    if (StrUtil.isBlank(raw) || raw.length() < 40) {
      discard(candidate, "视频无可引用文本");
      return true;
    }
    candidate.setRawText(raw);
    String brief = aiSupport.videoFactBrief(skillBindings, candidate.getTitle(), raw);
    if (StrUtil.isBlank(brief) || brief.contains("信息不足")) {
      discard(candidate, "视频事实速览失败");
      return true;
    }
    candidate.setFactBrief(brief.trim());
    return true;
  }

  private static void discard(RadarCollectCandidate candidate, String reason) {
    candidate.setDiscarded(true);
    candidate.setDiscardReason(reason);
    candidate.setRawText(null);
    log.info("雷达候选弃用 title={} reason={} url={}",
        candidate.getTitle(), reason, candidate.getUrl());
  }

  /** 内存视频解析目标：不写库，只回写候选。 */
  private static final class InMemoryVideoSink implements VideoParseSink {
    private final RadarCollectCandidate candidate;
    private final AtomicReference<String> rawText = new AtomicReference<>();
    private final AtomicReference<String> summary = new AtomicReference<>();
    private final AtomicReference<String> parseStatus = new AtomicReference<>(VideoParseStatus.NONE.getValue());
    private final AtomicReference<String> parseError = new AtomicReference<>();
    private final AtomicReference<String> contentKind = new AtomicReference<>();

    private InMemoryVideoSink(RadarCollectCandidate candidate) {
      this.candidate = candidate;
    }

    @Override
    public String getId() {
      return candidate.getTempId();
    }

    @Override
    public String getUrl() {
      return candidate.getUrl();
    }

    @Override
    public String getTitle() {
      return candidate.getTitle();
    }

    @Override
    public String getVideoRawText() {
      return rawText.get();
    }

    @Override
    public String getVideoContentKind() {
      return contentKind.get();
    }

    @Override
    public void setVideoRawText(String videoRawText) {
      rawText.set(videoRawText);
    }

    @Override
    public void setVideoContentKind(String videoContentKind) {
      contentKind.set(videoContentKind);
    }

    @Override
    public void setVideoParseStatus(String videoParseStatus) {
      parseStatus.set(videoParseStatus);
    }

    @Override
    public void setVideoParseStep(String videoParseStep) {
      // 内存目标不落步骤
    }

    @Override
    public void setVideoParseError(String videoParseError) {
      parseError.set(videoParseError);
    }

    @Override
    public void setVideoParsedAt(LocalDateTime videoParsedAt) {
      // 不需要
    }

    @Override
    public void setSummary(String summaryText) {
      summary.set(summaryText);
    }

    @Override
    public void setVideoDurationSeconds(Integer videoDurationSeconds) {
      // 内存目标不持久化时长
    }

    @Override
    public void persist() {
      // 不落库
    }
  }
}
