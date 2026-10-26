package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.workbench.job.facade.JobFacade;
import com.workbench.job.facade.constant.JobEntityTypes;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.job.facade.dto.job.HotNewsArticleRewriteJobInput;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.workbench.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.workbench.mode.hotnews.facade.constant.HotNewsArticleContentKinds;
import com.workbench.mode.hotnews.facade.constant.HotNewsArticleStatuses;
import com.workbench.mode.hotnews.facade.constant.HotNewsDocumentVersionSources;
import com.workbench.mode.hotnews.facade.dto.request.HotNewsRewriteRequest;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsRewriteResultView;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点文章 AI 改写入队与候选稿落库。
 */
@Service
@RequiredArgsConstructor
public class HotNewsRewriteCandidateService {

  private final JobFacade jobs;
  private final HotNewsCodec codec;
  private final HotNewsWorkContentService contents;
  private final HotNewsGenerationSettingsService generationSettings;
  private final HotNewsArticleViewAssembler views;

  /**
   * 入队热点文章 AI 改写任务。
   *
   * @param articleId 文章主键
   * @param body      改写请求；可为 null
   * @return 已入队 Job
   */
  @Transactional
  public JobView startArticleRewrite(String articleId, HotNewsRewriteRequest body) {
    contents.requireArticle(articleId);
    String instruction =
        body == null
            ? ""
            : StrUtil.nullToEmpty(body.getInstruction());
    return jobs.create(
        JobTypes.HOT_NEWS_ARTICLE_REWRITE,
        JobEntityTypes.HOT_NEWS_ARTICLE,
        articleId,
        HotNewsArticleRewriteJobInput.builder()
            .articleId(articleId)
            .instruction(StrUtil.blankToDefault(instruction, "优化热点文章表达，保持事实不变"))
            .build());
  }

  /**
   * 落库 AI 改写稿：更新原稿、保留公众号正文。
   *
   * @param articleId      文章主键
   * @param rewrittenDraft 改写后的原稿 Markdown
   * @param instruction    改写指令
   * @return 更新后的文章视图
   */
  @Transactional
  public HotNewsArticleView applyAiRewrite(
      String articleId, String rewrittenDraft, String instruction) {
    HotNewsArticleEntity article = contents.requireArticle(articleId);
    LocalDateTime now = LocalDateTime.now();
    String draftText = StrUtil.nullToEmpty(rewrittenDraft);
    String wechatBase =
        contents.resolveHotBody(article, HotNewsArticleContentKinds.WECHAT, article.getContent());
    String hint = StrUtil.blankToDefault(instruction, "按热点改写要求优化表达").trim();
    int nextVersion = (article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo()) + 1;
    article.setDraftContent(draftText);
    article.setDraftContentHash(HotNewsCodec.sha256(draftText));
    article.setContent(wechatBase);
    article.setContentHash(HotNewsCodec.sha256(wechatBase));
    article.setWordCount(HotNewsMarkdownUtil.countBodyChars(wechatBase));
    article.setCurrentVersionNo(nextVersion);
    Map<String, Object> settings = codec.readMap(generationSettings.preferGenerationSettingsJson(article));
    settings.put("lastRewriteInstruction", hint);
    settings.put("rewrittenAt", now.toString());
    article.setGenerationSettingsJson(codec.writeJson(settings));
    article.setStatus(HotNewsArticleStatuses.REWRITTEN);
    article.setUpdatedAt(now);
    contents.mirrorHotNewsDocuments(
        article, HotNewsDocumentVersionSources.REWRITE_CANDIDATE, "Hot news AI rewrite");
    return views.articleView(article);
  }

  /**
   * 基于指令生成改写候选草稿（本地拼接说明）。
   *
   * @param articleId   文章主键
   * @param instruction 改写说明
   * @return 改写结果视图
   */
  @Transactional
  public HotNewsRewriteResultView createRewriteCandidate(String articleId, String instruction) {
    HotNewsArticleEntity article = contents.requireArticle(articleId);
    LocalDateTime now = LocalDateTime.now();
    String draftBase = contents.resolveHotBody(article, HotNewsArticleContentKinds.DRAFT, article.getDraftContent());
    String wechatBase = contents.resolveHotBody(article, HotNewsArticleContentKinds.WECHAT, article.getContent());
    String base = StrUtil.isBlank(draftBase) ? wechatBase : draftBase;
    String hint = StrUtil.blankToDefault(instruction, "按热点改写要求优化表达").trim();
    String rewritten = StrUtil.isBlank(base)
        ? "# 改写稿\n\n" + hint + "\n"
        : base + "\n\n---\n改写说明：" + hint + "\n";
    int nextVersion = (article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo()) + 1;
    article.setDraftContent(rewritten);
    article.setDraftContentHash(HotNewsCodec.sha256(rewritten));
    article.setContent(wechatBase);
    article.setContentHash(HotNewsCodec.sha256(wechatBase));
    article.setCurrentVersionNo(nextVersion);
    Map<String, Object> settings = codec.readMap(generationSettings.preferGenerationSettingsJson(article));
    settings.put("lastRewriteInstruction", hint);
    settings.put("rewrittenAt", now.toString());
    article.setGenerationSettingsJson(codec.writeJson(settings));
    article.setStatus(HotNewsArticleStatuses.REWRITTEN);
    article.setUpdatedAt(now);
    contents.mirrorHotNewsDocuments(
        article, HotNewsDocumentVersionSources.REWRITE_CANDIDATE, "Hot news rewrite candidate");

    HotNewsArticleView view = views.articleView(article);
    return HotNewsRewriteResultView.builder()
        .id(view.getId())
        .documentId(view.getDocumentId())
        .draftDocumentId(view.getDraftDocumentId())
        .topicId(view.getTopicId())
        .briefId(view.getBriefId())
        .title(view.getTitle())
        .draftContent(view.getDraftContent())
        .draftContentHash(view.getDraftContentHash())
        .draftWordCount(view.getDraftWordCount())
        .content(view.getContent())
        .contentHash(view.getContentHash())
        .wordCount(view.getWordCount())
        .currentVersionNo(view.getCurrentVersionNo())
        .status(view.getStatus())
        .workflowRunId(view.getWorkflowRunId())
        .sourceSnapshot(view.getSourceSnapshot())
        .generationSettings(view.getGenerationSettings())
        .createdAt(view.getCreatedAt())
        .updatedAt(view.getUpdatedAt())
        .instruction(hint)
        .rewritten(true)
        .build();
  }
}
