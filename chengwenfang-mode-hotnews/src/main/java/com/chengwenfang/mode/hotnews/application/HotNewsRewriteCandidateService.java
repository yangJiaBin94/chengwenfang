package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobEntityTypes;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.HotNewsArticleRewriteJobInput;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleContentKinds;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleStatuses;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsDocumentVersionSources;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsGenerationSettingsKeys;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsRewriteRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点文章 AI 改写入队与候选稿落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsRewriteCandidateService {
  /** 任务门面。 */
  private final JobFacade jobs;
  /** JSON 编解码。 */
  private final HotNewsCodec codec;
  /** 正文读写。 */
  private final HotNewsWorkContentService contents;
  /** 生成设置。 */
  private final HotNewsGenerationSettingsService generationSettings;
  /** 文章视图组装。 */
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
    HotNewsArticleEntity article = contents.requireArticle(articleId);
    String draft = contents.resolveHotBody(
        article, HotNewsArticleContentKinds.DRAFT, article.getDraftContent());
    if (StrUtil.isBlank(draft)) {
      throw new BusinessException(HotNewsErrorCodes.DRAFT_EMPTY, "热点原稿为空，无法改写", 400);
    }
    String instruction =
        body == null
            ? ""
            : StrUtil.nullToEmpty(body.getInstruction());
    String advice =
        body == null
            ? ""
            : StrUtil.nullToEmpty(body.getAdvice());
    String defaultInstruction = StrUtil.isNotBlank(advice)
        ? "按优化建议改写热点原稿，保持事实不变"
        : "优化热点文章表达，保持事实不变";
    JobView job = jobs.create(
        JobTypes.HOT_NEWS_ARTICLE_REWRITE,
        JobEntityTypes.HOT_NEWS_ARTICLE,
        articleId,
        HotNewsArticleRewriteJobInput.builder()
            .articleId(articleId)
            .instruction(StrUtil.blankToDefault(instruction, defaultInstruction))
            .advice(advice)
            .build());
    log.info("热点改写任务已入队 articleId={} jobId={}", articleId, job.getId());
    return job;
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
    settings.put(HotNewsGenerationSettingsKeys.LAST_REWRITE_INSTRUCTION, hint);
    settings.put(HotNewsGenerationSettingsKeys.REWRITTEN_AT, now.toString());
    // 正文已变，显式清空旧诊断（含 DB 列），避免面板回填过期打分
    settings.put(HotNewsGenerationSettingsKeys.LAST_OPTIMIZE_CRITIQUE, null);
    article.setGenerationSettingsJson(codec.writeJson(settings));
    article.setStatus(HotNewsArticleStatuses.REWRITTEN);
    article.setUpdatedAt(now);
    contents.mirrorHotNewsDocuments(
        article, HotNewsDocumentVersionSources.REWRITE_CANDIDATE, "Hot news AI rewrite");
    log.info("热点 AI 改写稿已落库 articleId={} versionNo={}", articleId, nextVersion);
    return views.articleView(article);
  }
}
