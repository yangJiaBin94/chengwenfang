package com.chengwenfang.mode.hotnews.application;

import com.chengwenfang.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleContentUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsGenerationSettingsRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleVersionView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsRewriteResultView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点文章文档轨门面：正文、版本、壳投影与落库；内部委派作品/正文/设置/改写协作类。
 * <p>成文入队与骨架编排见 {@link HotNewsArticleService}。
 */
@Service
@RequiredArgsConstructor
public class HotNewsArticleDocumentService {

  /** 正文读写、版本与壳投影。 */
  private final HotNewsWorkContentService contents;
  /** 生成参数。 */
  private final HotNewsGenerationSettingsService generationSettings;
  /** AI 改写。 */
  private final HotNewsRewriteCandidateService rewrites;
  /** 视图组装。 */
  private final HotNewsArticleViewAssembler views;

  /**
   * 按 ID 加载文章；作品扩展行必须存在。
   *
   * @param articleId 文章主键（= works.id）
   * @return 文章实体
   */
  public HotNewsArticleEntity requireArticle(String articleId) {
    return contents.requireArticle(articleId);
  }

  /**
   * 将文章实体转为对外视图；解析快照/生成参数，并计算草稿词数。
   *
   * @param article 文章实体
   * @return 文章视图
   */
  public HotNewsArticleView articleView(HotNewsArticleEntity article) {
    return views.articleView(article);
  }

  /**
   * 按选题加载热点文章投影。
   *
   * @param topicId 选题 id
   * @return 壳实体列表（按 updatedAt 降序）
   */
  public List<HotNewsArticleEntity> listShellArticlesByTopic(String topicId) {
    return contents.listShellArticlesByTopic(topicId);
  }

  /**
   * 选题下最近更新的一篇热点文章投影。
   *
   * @param topicId 选题 id
   * @return 壳实体；无则 null
   */
  public HotNewsArticleEntity findLatestShellArticleByTopic(String topicId) {
    return contents.findLatestShellArticleByTopic(topicId);
  }

  /**
   * 按 brief 加载热点文章投影。
   *
   * @param briefId brief id
   * @return 壳实体列表（按 updatedAt 降序）
   */
  public List<HotNewsArticleEntity> listShellArticlesByBrief(String briefId) {
    return contents.listShellArticlesByBrief(briefId);
  }

  /**
   * brief 下最近更新的一篇热点文章投影。
   *
   * @param briefId brief id
   * @return 壳实体；无则 null
   */
  public HotNewsArticleEntity findLatestShellArticleByBrief(String briefId) {
    return contents.findLatestShellArticleByBrief(briefId);
  }

  /**
   * 更新公众号正文（content），带 baseContentHash 乐观锁冲突检测，并追加版本。
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
   * 按 kind（draft / wechat / content）更新双轨内容之一，并追加版本。
   *
   * @param articleId 文章主键
   * @param kind      draft 或 wechat
   * @param body      内容请求
   * @return 更新后的文章视图
   */
  @Transactional
  public HotNewsArticleView updateArticleContent(
      String articleId, String kind, HotNewsArticleContentUpdateRequest body) {
    return contents.updateArticleContent(articleId, kind, body);
  }

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
    return generationSettings.updateGenerationSettings(articleId, body);
  }

  /**
   * 列出热点文章版本历史，按 versionNo 倒序。
   *
   * @param articleId 文章主键
   * @return 版本视图列表
   */
  public List<HotNewsArticleVersionView> listArticleVersions(String articleId) {
    return contents.listArticleVersions(articleId);
  }

  /**
   * 列出热点文章版本历史；显式 documentId 仅用于校验/日志。
   *
   * @param articleId  文章主键
   * @param documentId 可选产出 id
   * @return 版本视图列表
   */
  public List<HotNewsArticleVersionView> listArticleVersions(String articleId, String documentId) {
    return contents.listArticleVersions(articleId, documentId);
  }

  /**
   * 双轨提交成文结果：写正文并将状态置为 ready。
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
    return rewrites.applyAiRewrite(articleId, rewrittenDraft, instruction);
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
    return rewrites.createRewriteCandidate(articleId, instruction);
  }

  /**
   * 新建热点文章：写 works / hotnews_works / 双轨产出基线。
   *
   * @param article 内存中的新文章投影
   */
  public void persistNewHotArticleDocuments(HotNewsArticleEntity article) {
    contents.persistNewHotArticleDocuments(article);
  }

  /**
   * 将热点壳元数据同步到 works / hotnews_works / settings（无正文变更）。
   *
   * @param article 已投影/内存中的热点文章
   */
  public void syncShellMetadata(HotNewsArticleEntity article) {
    contents.syncShellMetadata(article);
  }

  /**
   * 生成参数 JSON：读 work_generation_settings。
   *
   * @param article 热点文章
   * @return JSON 字符串；均可空
   */
  public String preferGenerationSettingsJson(HotNewsArticleEntity article) {
    return generationSettings.preferGenerationSettingsJson(article);
  }

  /**
   * 解析热点某轨正文。
   *
   * @param article    文章实体
   * @param kind       draft / wechat
   * @param legacyBody 内存投影正文
   * @return 正文（非 null）
   */
  public String resolveHotBody(HotNewsArticleEntity article, String kind, String legacyBody) {
    return contents.resolveHotBody(article, kind, legacyBody);
  }

  /**
   * 从作品表合成热点文章内存投影。
   *
   * @param articleId 热点文章 id
   * @return 投影；无则 null
   */
  public HotNewsArticleEntity projectHotArticleFromDocuments(String articleId) {
    return contents.projectHotArticleFromDocuments(articleId);
  }

  /**
   * 将热点文章双轨正文/元数据落库到 works / work_outputs。
   *
   * @param article 已落库的热点文章
   * @param source  来源码
   * @param reason  原因说明
   */
  public void mirrorHotNewsDocuments(HotNewsArticleEntity article, String source, String reason) {
    contents.mirrorHotNewsDocuments(article, source, reason);
  }
}
