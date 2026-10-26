package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.workbench.job.facade.JobFacade;
import com.workbench.job.facade.constant.JobEntityTypes;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.job.facade.dto.job.HotNewsArticleGenerationJobInput;
import com.workbench.job.facade.dto.job.JobPayloads;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.workbench.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.workbench.mode.hotnews.facade.constant.HotNewsArticleJobKeys;
import com.workbench.mode.hotnews.facade.constant.HotNewsArticleStatuses;
import com.workbench.mode.hotnews.facade.dto.request.HotNewsGenerationRunRequest;
import com.workbench.mode.hotnews.facade.dto.request.HotNewsGenerationSettingsRequest;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.workbench.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.workbench.mode.hotnews.facade.enums.HotNewsWordCountOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点文章生成参数读写、骨架默认同步与成文任务入队。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsGenerationSettingsService {

  /** JSON/哈希助手。 */
  private final HotNewsCodec codec;
  /** 作品产出存储。 */
  private final HotNewsWorkStore workStore;
  /** 视图组装。 */
  private final HotNewsArticleViewAssembler views;
  /** 正文与壳投影。 */
  private final HotNewsWorkContentService contents;
  /** 任务门面。 */
  private final JobFacade jobs;

  /**
   * 合并更新文章生成参数。
   *
   * @param articleId 文章主键
   * @param body      生成设置补丁
   * @return 更新后的文章视图
   */
  @Transactional
  public HotNewsArticleView updateGenerationSettings(
      String articleId, HotNewsGenerationSettingsRequest body) {
    HotNewsArticleEntity article = contents.requireArticle(articleId);
    Map<String, Object> settings = codec.readMap(preferGenerationSettingsJson(article));
    if (body != null) {
      if (body.getWordCountOption() != null) {
        settings.put("wordCountOption", body.getWordCountOption());
      }
      if (body.getImageCountOption() != null) {
        settings.put("imageCountOption", body.getImageCountOption());
      }
      if (StrUtil.isNotBlank(body.getDocumentId())) {
        log.info("更新热点生成设置 articleId={} documentId={}", articleId, body.getDocumentId());
      }
    }
    article.setGenerationSettingsJson(codec.writeJson(settings));
    article.setUpdatedAt(LocalDateTime.now());
    contents.syncShellMetadata(article);
    return views.articleView(article);
  }

  /**
   * 生成参数 JSON：读 work_generation_settings。
   *
   * @param article 热点文章
   * @return JSON 字符串；均可空
   */
  public String preferGenerationSettingsJson(HotNewsArticleEntity article) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return null;
    }
    Map<String, Object> settings = workStore.readSettingsMap(article.getId());
    if (!settings.isEmpty()) {
      return codec.writeJson(settings);
    }
    return article.getGenerationSettingsJson();
  }

  /**
   * 由骨架生成工作区默认生成设置（字数、正文插图档、风格、封面要点）。
   *
   * @param skeleton 文章骨架，可空
   * @return 生成设置 Map
   */
  public Map<String, Object> settingsFromSkeleton(HotNewsArticleSkeletonView skeleton) {
    Map<String, Object> settings = new LinkedHashMap<>();
    String wordOption = skeleton == null
        ? HotNewsWordCountOption.defaultOption().getValue()
        : HotNewsWordCountOption.normalize(skeleton.getRecommendedWordCountOption());
    String imageOption = skeleton == null
        ? HotNewsImageCountOption.defaultOption().getValue()
        : HotNewsImageCountOption.normalize(skeleton.getRecommendedImageCountOption());
    settings.put("wordCountOption", wordOption);
    settings.put("imageCountOption", imageOption);
    if (skeleton != null) {
      if (StrUtil.isNotBlank(skeleton.getWorkingTitle())) {
        settings.put("workingTitle", skeleton.getWorkingTitle());
      }
      if (StrUtil.isNotBlank(skeleton.getIllustrationStyle())) {
        settings.put("illustrationStyle", skeleton.getIllustrationStyle());
      }
      if (StrUtil.isNotBlank(skeleton.getCoverBrief())) {
        settings.put("coverBrief", skeleton.getCoverBrief());
      }
      if (StrUtil.isNotBlank(skeleton.getImagePromptRequirements())) {
        settings.put("imagePromptRequirements", skeleton.getImagePromptRequirements());
      }
    }
    settings.putIfAbsent("illustrationStyle", HotNewsMarkdownUtil.DEFAULT_ILLUSTRATION_STYLE);
    return settings;
  }

  /**
   * 将骨架推荐写回选题下已有文章壳，便于进入工作区默认选中。
   *
   * @param topicId  选题 ID
   * @param skeleton 文章骨架
   */
  public void syncArticleSettingsFromSkeleton(
      String topicId, HotNewsArticleSkeletonView skeleton) {
    if (StrUtil.isBlank(topicId) || skeleton == null) {
      return;
    }
    HotNewsArticleEntity article = contents.findLatestShellArticleByTopic(topicId);
    if (article == null) {
      return;
    }
    Map<String, Object> merged = codec.readMap(preferGenerationSettingsJson(article));
    merged.putAll(settingsFromSkeleton(skeleton));
    article.setGenerationSettingsJson(codec.writeJson(merged));
    if (StrUtil.isBlank(article.getTitle()) && StrUtil.isNotBlank(skeleton.getWorkingTitle())) {
      article.setTitle(skeleton.getWorkingTitle());
    }
    article.setUpdatedAt(LocalDateTime.now());
    contents.syncShellMetadata(article);
  }

  /**
   * 启动/重跑文章生成任务：写 status=generating 并入队，载荷含 scope 与生成选项。
   *
   * @param articleId 文章主键，同时作为任务 entityId
   * @param body      运行请求；可带 scope / wordCountOption / imageCountOption
   * @return 新建的生成任务视图
   * @throws com.workbench.common.BusinessException 文章不存在时（ARTICLE_NOT_FOUND / 404）
   */
  @Transactional
  public JobView startGenerationRun(String articleId, HotNewsGenerationRunRequest body) {
    HotNewsArticleGenerationJobInput input = new HotNewsArticleGenerationJobInput();
    if (body != null) {
      if (StrUtil.isNotBlank(body.getDocumentId())) {
        input.put("documentId", body.getDocumentId());
      }
      if (body.getScope() != null) {
        input.put("scope", body.getScope());
      }
      if (body.getWordCountOption() != null) {
        input.put("wordCountOption", body.getWordCountOption());
      }
      if (body.getImageCountOption() != null) {
        input.put("imageCountOption", body.getImageCountOption());
      }
    }
    return startGenerationRun(articleId, input);
  }

  /**
   * 启动/重跑文章生成任务（类型化载荷）。
   *
   * @param articleId 文章主键
   * @param input     任务入参；可空
   * @return 新建的生成任务视图
   */
  @Transactional
  public JobView startGenerationRun(String articleId, Object input) {
    HotNewsArticleEntity article = contents.requireArticle(articleId);
    HotNewsArticleGenerationJobInput payload =
        HotNewsArticleGenerationJobInput.from(JobPayloads.asMap(input));
    payload.put(HotNewsArticleJobKeys.ARTICLE_ID, articleId);
    payload.putIfAbsent(HotNewsArticleJobKeys.BRIEF_ID, article.getBriefId());
    payload.putIfAbsent(HotNewsArticleJobKeys.TOPIC_ID, article.getTopicId());
    article.setStatus(HotNewsArticleStatuses.GENERATING);
    article.setUpdatedAt(LocalDateTime.now());
    // 先落库双轨产出，再写入真实产出 id 供下游配图/审核与前端深链
    contents.syncShellMetadata(article);
    payload.putIfAbsent(HotNewsArticleJobKeys.DOCUMENT_ID, workStore.wechatOutputId(articleId));
    payload.putIfAbsent(HotNewsArticleJobKeys.DRAFT_DOCUMENT_ID, workStore.draftOutputId(articleId));
    log.info("热点生成任务已入队 articleId={} documentId={}",
        articleId, payload.get("documentId"));
    return jobs.create(
        JobTypes.HOT_NEWS_ARTICLE_GENERATION,
        JobEntityTypes.HOT_NEWS_ARTICLE,
        articleId,
        payload);
  }

  /**
   * 列出该文章相关的生成任务（从全局同类型任务中按 entityId 过滤）。
   *
   * @param articleId 文章主键，对应 Job.entityId
   * @return 匹配的任务视图列表（最多从最近 100 条同类型任务中筛）
   * @throws com.workbench.common.BusinessException 文章不存在时（ARTICLE_NOT_FOUND / 404）
   */
  public List<JobView> listGenerationRuns(String articleId) {
    contents.requireArticle(articleId);
    // Job 模块按类型列表，再按文章 ID 过滤
    return jobs.listByType(JobTypes.HOT_NEWS_ARTICLE_GENERATION, 100).stream()
        .filter(job -> articleId.equals(job.getEntityId()))
        .toList();
  }

  /**
   * 热点成文 Job 入参：身份键 + 双轨 documentId（供深链 /documents）。
   *
   * @param articleId 热点文章 id
   * @param briefId   brief id；可空
   * @param topicId   选题 id；可空
   * @return 类型化入参
   */
  public HotNewsArticleGenerationJobInput hotArticleGenerationJobInput(
      String articleId, String briefId, String topicId) {
    HotNewsArticleGenerationJobInput payload = new HotNewsArticleGenerationJobInput();
    payload.put(HotNewsArticleJobKeys.ARTICLE_ID, articleId);
    if (StrUtil.isNotBlank(briefId)) {
      payload.put(HotNewsArticleJobKeys.BRIEF_ID, briefId);
    }
    if (StrUtil.isNotBlank(topicId)) {
      payload.put(HotNewsArticleJobKeys.TOPIC_ID, topicId);
    }
    payload.put(HotNewsArticleJobKeys.DOCUMENT_ID, workStore.wechatOutputId(articleId));
    payload.put(HotNewsArticleJobKeys.DRAFT_DOCUMENT_ID, workStore.draftOutputId(articleId));
    return payload;
  }
}
