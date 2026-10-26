package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.content.facade.dto.view.WorkOutputVersionSnapshot;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleContentKinds;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleStatuses;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsDocumentVersionSources;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleContentUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleVersionView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点文章正文读写、版本列表与壳投影落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsWorkContentService {

  /** 作品产出存储。 */
  private final HotNewsWorkStore workStore;
  /** 视图组装。 */
  private final HotNewsArticleViewAssembler viewAssembler;

  /**
   * 更新公众号正文（content），带 baseContentHash 乐观锁冲突检测，并追加版本。
   *
   * @param articleId 文章主键
   * @param body      更新请求
   * @return 更新后的文章视图
   */
  @Transactional
  public HotNewsArticleView updateHotArticle(String articleId, HotNewsArticleUpdateRequest body) {
    HotNewsArticleEntity article = requireArticle(articleId);
    HotNewsArticleUpdateRequest req = body == null ? new HotNewsArticleUpdateRequest() : body;
    String currentWechat = resolveHotBody(
        article, HotNewsArticleContentKinds.WECHAT, article.getContent());
    String content = req.getContent() == null ? currentWechat : req.getContent();
    String baseHash = req.getBaseContentHash();
    String currentHash = StrUtil.blankToDefault(article.getContentHash(), HotNewsCodec.sha256(currentWechat));
    if (baseHash != null && !baseHash.equals(currentHash)) {
      throw new BusinessException(ErrorCodes.ARTICLE_STALE, "文章已被其他地方更新", 409);
    }
    LocalDateTime now = LocalDateTime.now();
    int nextVersion = (article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo()) + 1;
    String draft = resolveHotBody(article, HotNewsArticleContentKinds.DRAFT, article.getDraftContent());
    article.setContent(content);
    article.setDraftContent(draft);
    article.setContentHash(HotNewsCodec.sha256(content));
    article.setDraftContentHash(HotNewsCodec.sha256(draft));
    article.setWordCount(StrUtil.isBlank(content) ? 0 : content.trim().split("\\s+").length);
    article.setCurrentVersionNo(nextVersion);
    article.setUpdatedAt(now);
    mirrorHotNewsDocuments(
        article, HotNewsDocumentVersionSources.MANUAL_EDIT, "Hot news wechat update");
    return viewAssembler.articleView(article);
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
    HotNewsArticleEntity article = requireArticle(articleId);
    if (!HotNewsArticleContentKinds.isValidUpdateKind(kind)) {
      throw new BusinessException(ErrorCodes.INVALID_CONTENT_KIND, "kind 必须为 draft 或 wechat", 400);
    }
    HotNewsArticleContentUpdateRequest req =
        body == null ? new HotNewsArticleContentUpdateRequest() : body;
    String text = req.getContent() == null ? "" : req.getContent();
    LocalDateTime now = LocalDateTime.now();
    int nextVersion = (article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo()) + 1;
    String draft = resolveHotBody(article, HotNewsArticleContentKinds.DRAFT, article.getDraftContent());
    String wechat = resolveHotBody(article, HotNewsArticleContentKinds.WECHAT, article.getContent());
    String field = HotNewsArticleContentKinds.normalize(kind);
    if (HotNewsArticleContentKinds.isDraft(field)) {
      draft = text;
    } else {
      wechat = text;
    }
    article.setDraftContent(draft);
    article.setDraftContentHash(HotNewsCodec.sha256(draft));
    article.setContent(wechat);
    article.setContentHash(HotNewsCodec.sha256(wechat));
    article.setWordCount(StrUtil.isBlank(wechat) ? 0 : wechat.trim().split("\\s+").length);
    article.setCurrentVersionNo(nextVersion);
    article.setUpdatedAt(now);
    mirrorHotNewsDocuments(
        article,
        HotNewsDocumentVersionSources.MANUAL_EDIT,
        "Hot news content update kind=" + field);
    return viewAssembler.articleView(article);
  }

  /**
   * 列出热点文章版本历史，按 versionNo 倒序。
   *
   * @param articleId 文章主键
   * @return 版本视图列表
   */
  public List<HotNewsArticleVersionView> listArticleVersions(String articleId) {
    return listArticleVersions(articleId, null);
  }

  /**
   * 列出热点文章版本历史；显式 documentId 仅用于校验/日志。
   *
   * @param articleId  文章主键
   * @param documentId 可选产出 id
   * @return 版本视图列表
   */
  public List<HotNewsArticleVersionView> listArticleVersions(String articleId, String documentId) {
    requireArticle(articleId);
    String wechatDocumentId = workStore.wechatOutputId(articleId);
    if (StrUtil.isNotBlank(documentId) && !documentId.equals(wechatDocumentId)) {
      String draftDocumentId = workStore.draftOutputId(articleId);
      if (!documentId.equals(draftDocumentId)) {
        log.warn("热点版本列表 documentId 与文章轨不一致 articleId={} documentId={}",
            articleId, documentId);
      }
    }
    List<WorkOutputVersionSnapshot> draftVersions =
        workStore.listVersions(articleId, HotNewsArticleContentKinds.DRAFT);
    List<WorkOutputVersionSnapshot> wechatVersions =
        workStore.listVersions(articleId, HotNewsArticleContentKinds.WECHAT);
    Map<Integer, WorkOutputVersionSnapshot> draftByNo = draftVersions.stream()
        .collect(Collectors.toMap(
            WorkOutputVersionSnapshot::getVersionNo, item -> item, (left, right) -> left));
    Map<Integer, WorkOutputVersionSnapshot> wechatByNo = wechatVersions.stream()
        .collect(Collectors.toMap(
            WorkOutputVersionSnapshot::getVersionNo, item -> item, (left, right) -> left));
    Set<Integer> versionNos = new TreeSet<>(Comparator.reverseOrder());
    versionNos.addAll(draftByNo.keySet());
    versionNos.addAll(wechatByNo.keySet());
    List<HotNewsArticleVersionView> views = new ArrayList<>();
    for (Integer versionNo : versionNos) {
      WorkOutputVersionSnapshot draft = draftByNo.get(versionNo);
      WorkOutputVersionSnapshot wechat = wechatByNo.get(versionNo);
      String draftBody = draft == null ? "" : draft.getBodyMd();
      String wechatBody = wechat == null ? "" : wechat.getBodyMd();
      views.add(HotNewsArticleVersionView.builder()
          .id(wechat != null ? wechat.getId() : (draft == null ? null : draft.getId()))
          .articleId(articleId)
          .versionNo(versionNo)
          .draftContent(draftBody)
          .draftContentHash(draft == null ? HotNewsCodec.sha256(draftBody) : draft.getContentHash())
          .content(wechatBody)
          .contentHash(wechat == null ? HotNewsCodec.sha256(wechatBody) : wechat.getContentHash())
          .source(wechat != null ? wechat.getSource() : (draft == null ? null : draft.getSource()))
          .reason(wechat != null ? wechat.getReason() : (draft == null ? null : draft.getReason()))
          .workflowRunId(wechat != null ? wechat.getWorkflowRunId() : (draft == null ? null : draft.getWorkflowRunId()))
          .createdAt(wechat != null ? wechat.getCreatedAt() : (draft == null ? null : draft.getCreatedAt()))
          .build());
    }
    return views;
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
    HotNewsArticleEntity article = requireArticle(articleId);
    LocalDateTime now = LocalDateTime.now();
    String draftText = draft == null ? "" : draft;
    String wechatText = wechat == null ? "" : wechat;
    int nextVersion = (article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo()) + 1;
    article.setDraftContent(draftText);
    article.setDraftContentHash(HotNewsCodec.sha256(draftText));
    article.setContent(wechatText);
    article.setContentHash(HotNewsCodec.sha256(wechatText));
    article.setWordCount(HotNewsMarkdownUtil.countBodyChars(wechatText));
    article.setCurrentVersionNo(nextVersion);
    article.setStatus(HotNewsArticleStatuses.READY);
    article.setUpdatedAt(now);
    mirrorHotNewsDocuments(
        article, HotNewsDocumentVersionSources.AI_GENERATION, "Hot news paired commit");
    return viewAssembler.articleView(article);
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
    if (article != null && StrUtil.isNotBlank(article.getId())) {
      String body = workStore.readBody(article.getId(), kind);
      if (body != null) {
        return body;
      }
    }
    return legacyBody == null ? "" : legacyBody;
  }

  /**
   * 按 ID 加载文章；作品扩展行必须存在。
   *
   * @param articleId 文章主键（= works.id）
   * @return 文章实体
   * @throws BusinessException 文章不存在时
   */
  public HotNewsArticleEntity requireArticle(String articleId) {
    if (StrUtil.isBlank(articleId)) {
      throw new BusinessException(ErrorCodes.ARTICLE_NOT_FOUND, "文章不存在", 404);
    }
    HotNewsArticleEntity article = workStore.load(articleId);
    if (article == null) {
      throw new BusinessException(ErrorCodes.ARTICLE_NOT_FOUND, "文章不存在", 404);
    }
    return article;
  }

  /**
   * 按选题加载热点文章投影。
   *
   * @param topicId 选题 id
   * @return 壳实体列表（按 updatedAt 降序）
   */
  public List<HotNewsArticleEntity> listShellArticlesByTopic(String topicId) {
    if (StrUtil.isBlank(topicId)) {
      return List.of();
    }
    return projectByIds(workStore.listIdsByTopic(topicId));
  }

  /**
   * 选题下最近更新的一篇热点文章投影。
   *
   * @param topicId 选题 id
   * @return 壳实体；无则 null
   */
  public HotNewsArticleEntity findLatestShellArticleByTopic(String topicId) {
    List<HotNewsArticleEntity> articles = listShellArticlesByTopic(topicId);
    return articles.isEmpty() ? null : articles.get(0);
  }

  /**
   * 按 brief 加载热点文章投影。
   *
   * @param briefId brief id
   * @return 壳实体列表（按 updatedAt 降序）
   */
  public List<HotNewsArticleEntity> listShellArticlesByBrief(String briefId) {
    if (StrUtil.isBlank(briefId)) {
      return List.of();
    }
    return projectByIds(workStore.listIdsByBrief(briefId));
  }

  /**
   * brief 下最近更新的一篇热点文章投影。
   *
   * @param briefId brief id
   * @return 壳实体；无则 null
   */
  public HotNewsArticleEntity findLatestShellArticleByBrief(String briefId) {
    List<HotNewsArticleEntity> articles = listShellArticlesByBrief(briefId);
    return articles.isEmpty() ? null : articles.get(0);
  }

  /**
   * 新建热点文章：写 works / hotnews_works / 双轨产出基线。
   *
   * @param article 内存中的新文章投影
   */
  public void persistNewHotArticleDocuments(HotNewsArticleEntity article) {
    mirrorHotNewsDocuments(
        article, HotNewsDocumentVersionSources.BASELINE, "Hot news article document baseline");
  }

  /**
   * 将热点壳元数据同步到 works / hotnews_works / settings（无正文变更）。
   *
   * @param article 已投影/内存中的热点文章
   */
  public void syncShellMetadata(HotNewsArticleEntity article) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return;
    }
    workStore.syncMetadata(article);
  }

  /**
   * 从作品表合成热点文章内存投影。
   *
   * @param articleId 热点文章 id
   * @return 投影；无则 null
   */
  public HotNewsArticleEntity projectHotArticleFromDocuments(String articleId) {
    return workStore.load(articleId);
  }

  /**
   * 将热点文章双轨正文/元数据落库到 works / work_outputs。
   *
   * @param article 已落库的热点文章
   * @param source  来源码
   * @param reason  原因说明
   */
  public void mirrorHotNewsDocuments(HotNewsArticleEntity article, String source, String reason) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return;
    }
    workStore.persist(article, source, reason, true);
  }

  /**
   * 按 id 列表加载投影。
   *
   * @param ids 作品 id 列表
   * @return 投影列表
   */
  private List<HotNewsArticleEntity> projectByIds(List<String> ids) {
    if (CollUtil.isEmpty(ids)) {
      return List.of();
    }
    List<HotNewsArticleEntity> projected = new ArrayList<>();
    for (String id : ids) {
      HotNewsArticleEntity row = workStore.load(id);
      if (row != null) {
        projected.add(row);
      }
    }
    return projected;
  }
}
