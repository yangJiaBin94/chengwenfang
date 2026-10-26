package com.chengwenfang.mode.hotnews.application;

import com.chengwenfang.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleContentUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsGenerationRunRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsGenerationSettingsRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsRewriteRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleVersionView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsRewriteResultView;
import com.chengwenfang.job.facade.dto.view.JobView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点文章编排门面：骨架、成文入队、列表/详情；内部委派作品/正文/设置/改写协作类。
 * <p>由 {@link HotNewsFacadeImpl} 薄委托；对外保持原公开方法签名。
 */
@Service
@RequiredArgsConstructor
public class HotNewsArticleService {

  /** 作品创建与查询。 */
  private final HotNewsWorkService works;
  /** 正文读写与版本。 */
  private final HotNewsWorkContentService contents;
  /** 生成参数。 */
  private final HotNewsGenerationSettingsService generationSettings;
  /** AI 改写。 */
  private final HotNewsRewriteCandidateService rewrites;
  /** 视图组装。 */
  private final HotNewsArticleViewAssembler views;

  /**
   * 读取选题上已保存的文章骨架。
   *
   * @param topicId 选题主键
   * @return 骨架视图；无 JSON 则为 null
   */
  public HotNewsArticleSkeletonView getArticleSkeleton(String topicId) {
    return works.getArticleSkeleton(topicId);
  }

  /**
   * 拟定并落库文章骨架（无主题上下文）。
   *
   * @param topicId 选题主键
   * @return 骨架视图
   */
  @Transactional
  public HotNewsArticleSkeletonView buildArticleSkeleton(String topicId) {
    return works.buildArticleSkeleton(topicId);
  }

  /**
   * 拟定并落库文章骨架，同步生成设置与简报。
   *
   * @param topicId      选题主键
   * @param themeContext 主题上下文；可空
   * @return 骨架视图
   */
  @Transactional
  public HotNewsArticleSkeletonView buildArticleSkeleton(String topicId, String themeContext) {
    return works.buildArticleSkeleton(topicId, themeContext);
  }

  /**
   * 由简报创建或复用热点文章并入队生成任务。
   *
   * @param briefId 简报 id
   * @return 新建的成文任务
   */
  @Transactional
  public JobView generateArticleFromBrief(String briefId) {
    return works.generateArticleFromBrief(briefId);
  }

  /**
   * 由选题创建或复用热点文章并入队生成；简报仅作外键壳，不要求 confirmed。
   *
   * @param topicId 选题主键
   * @return 新建的成文任务
   */
  @Transactional
  public JobView generateArticleFromTopic(String topicId) {
    return works.generateArticleFromTopic(topicId);
  }

  /**
   * 列出选题下的热点文章，按更新时间倒序。
   *
   * @param topicId 选题主键
   * @return 文章视图列表
   */
  public List<HotNewsArticleView> listArticlesByTopic(String topicId) {
    return works.listArticlesByTopic(topicId);
  }

  /**
   * 获取热点文章详情。
   *
   * @param articleId 文章主键
   * @return 文章视图（含草稿/公众号双轨内容与生成参数）
   */
  public HotNewsArticleView getHotArticle(String articleId) {
    return works.getHotArticle(articleId);
  }

  /**
   * 更新公众号正文。
   *
   * @param articleId 文章主键
   * @param body      更新请求
   * @return 更新后的文章视图
   */
  @Transactional
  public HotNewsArticleView updateHotArticle(String articleId, HotNewsArticleUpdateRequest body) {
    return contents.updateHotArticle(articleId, body);
  }

  /**
   * 按 kind 更新双轨内容之一。
   *
   * @param articleId 文章主键
   * @param kind      draft / wechat / content
   * @param body      内容请求
   * @return 更新后的文章视图
   */
  @Transactional
  public HotNewsArticleView updateArticleContent(
      String articleId, String kind, HotNewsArticleContentUpdateRequest body) {
    return contents.updateArticleContent(articleId, kind, body);
  }

  /**
   * 合并更新生成参数。
   *
   * @param articleId 文章主键
   * @param body      生成设置补丁
   * @return 更新后的文章视图
   */
  @Transactional
  public HotNewsArticleView updateGenerationSettings(
      String articleId, HotNewsGenerationSettingsRequest body) {
    return generationSettings.updateGenerationSettings(articleId, body);
  }

  /**
   * 启动/重跑文章生成任务：写 status=generating 并入队，载荷含 scope 与生成选项。
   *
   * @param articleId 文章主键，同时作为任务 entityId
   * @param body      运行请求；可带 scope / wordCountOption / imageCountOption
   * @return 新建的生成任务视图
   */
  @Transactional
  public JobView startGenerationRun(String articleId, HotNewsGenerationRunRequest body) {
    return generationSettings.startGenerationRun(articleId, body);
  }

  /**
   * 启动/重跑文章生成任务（Map 载荷）。
   *
   * @param articleId 文章主键
   * @param input     任务入参；可空
   * @return 新建的生成任务视图
   */
  @Transactional
  public JobView startGenerationRun(String articleId, Object input) {
    return generationSettings.startGenerationRun(articleId, input);
  }

  /**
   * 确保选题下有一篇热点文章（无则建 draft 基线；有则按骨架合并默认生成设置）。
   *
   * @param topicId 选题主键
   * @return 文章视图
   */
  @Transactional
  public HotNewsArticleView ensureArticleForTopic(String topicId) {
    return works.ensureArticleForTopic(topicId);
  }

  /**
   * 列出该文章相关的生成任务（从全局同类型任务中按 entityId 过滤）。
   *
   * @param articleId 文章主键，对应 Job.entityId
   * @return 匹配的任务视图列表
   */
  public List<JobView> listGenerationRuns(String articleId) {
    return generationSettings.listGenerationRuns(articleId);
  }

  /**
   * 列出热点文章版本历史。
   *
   * @param articleId 文章主键
   * @return 版本视图列表
   */
  public List<HotNewsArticleVersionView> listArticleVersions(String articleId) {
    return contents.listArticleVersions(articleId);
  }

  /**
   * 列出热点文章版本历史。
   *
   * @param articleId  文章主键
   * @param documentId 可选文档 id
   * @return 版本视图列表
   */
  public List<HotNewsArticleVersionView> listArticleVersions(String articleId, String documentId) {
    return contents.listArticleVersions(articleId, documentId);
  }

  /**
   * 按 ID 加载文章。
   *
   * @param articleId 文章主键
   * @return 文章实体
   */
  public HotNewsArticleEntity requireArticle(String articleId) {
    return contents.requireArticle(articleId);
  }

  /**
   * 双轨提交成文结果。
   *
   * @param articleId 文章主键
   * @param draft     草稿轨正文
   * @param wechat    公众号轨正文
   * @return 提交后的文章视图
   */
  @Transactional
  public HotNewsArticleView commitHotNewsArticle(String articleId, String draft, String wechat) {
    return contents.commitHotNewsArticle(articleId, draft, wechat);
  }

  /**
   * 入队热点文章 AI 改写任务。
   *
   * @param articleId 文章主键
   * @param body      改写请求；可为 null
   * @return 已入队 Job
   */
  @Transactional
  public JobView startArticleRewrite(String articleId, HotNewsRewriteRequest body) {
    return rewrites.startArticleRewrite(articleId, body);
  }

  /**
   * 落库 AI 改写结果。
   *
   * @param articleId      文章主键
   * @param rewrittenDraft 改写后原稿
   * @param instruction    改写指令
   * @return 文章视图
   */
  @Transactional
  public HotNewsArticleView applyAiRewrite(
      String articleId, String rewrittenDraft, String instruction) {
    return rewrites.applyAiRewrite(articleId, rewrittenDraft, instruction);
  }

  /**
   * 生成改写候选草稿。
   *
   * @param articleId   文章主键
   * @param instruction 改写说明
   * @return 改写结果视图
   */
  @Transactional
  public HotNewsRewriteResultView createRewriteCandidate(String articleId, String instruction) {
    return rewrites.createRewriteCandidate(articleId, instruction);
  }

  /**
   * 将骨架 JSON 解析为视图；解析失败时回退为字段投影。
   *
   * @param topicId 选题主键
   * @param json    骨架 JSON
   * @return 骨架视图；空白 JSON 为 null
   */
  public HotNewsArticleSkeletonView skeletonView(String topicId, String json) {
    return views.skeletonView(topicId, json);
  }

  /**
   * 将文章实体转为对外视图。
   *
   * @param article 文章实体
   * @return 文章视图
   */
  public HotNewsArticleView articleView(HotNewsArticleEntity article) {
    return views.articleView(article);
  }
}
