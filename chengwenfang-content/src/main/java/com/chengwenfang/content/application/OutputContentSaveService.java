package com.chengwenfang.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.domain.entity.ArticleEntity;
import com.chengwenfang.content.domain.entity.ArticleVersionEntity;
import com.chengwenfang.content.domain.entity.CourseEntity;
import com.chengwenfang.content.domain.entity.WorkOutputEntity;
import com.chengwenfang.content.domain.entity.WorkOutputVersionEntity;
import com.chengwenfang.content.facade.constant.CourseOutputChannels;
import com.chengwenfang.content.facade.dto.request.ArticleUpdateRequest;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.dto.view.CourseGenerationCommitView;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import com.chengwenfang.content.facade.enums.ArticleKind;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputVersionMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产出正文保存：读写文章、生成提交与版本落库。
 */
@Service
public class OutputContentSaveService {
  private final CourseStore courseStore;
  private final WorkOutputMapper workOutputMapper;
  private final WorkOutputVersionMapper workOutputVersionMapper;
  private final WorkContentStoreImpl workContents;
  private final CourseShellService shell;
  private final ContentEntityUtil support;
  private final ContentViewMapper views;
  private final ThemeVisibilityGuard visibility;

  /**
   * @param courseStore              课程存储
   * @param workOutputMapper         产出壳 Mapper
   * @param workOutputVersionMapper  产出版本 Mapper
   * @param workContents             统一文档存储
   * @param shell                    课壳服务
   * @param support                  实体协作
   * @param views                    视图映射
   * @param visibility               主题可见性校验
   */
  public OutputContentSaveService(
      CourseStore courseStore,
      WorkOutputMapper workOutputMapper,
      WorkOutputVersionMapper workOutputVersionMapper,
      WorkContentStoreImpl workContents,
      CourseShellService shell,
      ContentEntityUtil support,
      ContentViewMapper views,
      ThemeVisibilityGuard visibility) {
    this.courseStore = courseStore;
    this.workOutputMapper = workOutputMapper;
    this.workOutputVersionMapper = workOutputVersionMapper;
    this.workContents = workContents;
    this.shell = shell;
    this.support = support;
    this.views = views;
    this.visibility = visibility;
  }

  /**
   * 获取文章详情。
   *
   * @param articleId 文章 id
   * @return 文章视图
   * @throws BusinessException 文章不存在时 404
   */
  public ArticleView getArticle(String articleId) {
    visibility.assertThemeActiveForArticleId(articleId);
    // 缺失则 404，存在则转视图（正文优先 content_documents）
    ArticleEntity article = support.requireArticle(articleId);
    return toArticleViewPreferringDocuments(article);
  }

  /**
   * 更新文章标题/正文；带 versionToken 乐观锁，正文变更时写版本。
   *
   * @param articleId 文章 id
   * @param body      更新请求；null 按空对象
   * @return 更新后的文章视图
   * @throws BusinessException 文章不存在或 versionToken 冲突时
   */
  @Transactional
  public ArticleView updateArticle(String articleId, ArticleUpdateRequest body) {
    visibility.assertThemeActiveForArticleId(articleId);
    ArticleEntity article = support.requireArticle(articleId);
    // 乐观锁权威在 content_documents（壳 version_token 已停写）
    support.overlayVersionIdentityFromDocuments(article);
    ArticleUpdateRequest req = body == null ? new ArticleUpdateRequest() : body;
    // 调用方带了 token 则校验乐观锁
    if (req.getVersionToken() != null) {
      long token = req.getVersionToken();
      if (article.getVersionToken() != null && article.getVersionToken() != token) {
        throw new BusinessException(ErrorCodes.VERSION_CONFLICT, "文章已被其他写作者修改", 409);
      }
    }
    LocalDateTime now = LocalDateTime.now();
    boolean contentChanged = false;
    if (req.getTitle() != null) {
      article.setTitle(req.getTitle());
    }
    if (req.getContent() != null) {
      // 与 content_documents 当前正文比较，勿用已停写的旧列
      overlayBodyFromDocuments(article);
      String content = req.getContent();
      String previous = article.getContent() == null ? "" : article.getContent();
      if (!content.equals(previous)) {
        contentChanged = true;
      }
      article.setContent(content);
      article.setWordCount(content.length());
      article.setContentHash(ContentEntityUtil.sha256(content));
    }
    // 任意更新都递增 token，供下次冲突检测
    article.setVersionToken((article.getVersionToken() == null ? 0 : article.getVersionToken()) + 1);
    article.setUpdatedAt(now);
    if (contentChanged) {
      String source = req.getSource() == null ? "manual_edit" : req.getSource();
      String reason = req.getReason() == null ? "手动编辑" : req.getReason();
      insertContentVersion(article, source, reason, now);
    }
    if (req.getTitle() != null || contentChanged) {
      syncOutputShell(article);
    }
    return toArticleViewPreferringDocuments(article);
  }

  /**
   * 提交课程生成结果，写入原稿与公众号正文并将课程标为已生成。
   *
   * @param courseId       课程 id
   * @param draftMarkdown  原稿 Markdown
   * @param wechatMarkdown 公众号稿 Markdown
   * @return 提交结果（含两篇文章视图）
   */
  @Transactional
  public CourseGenerationCommitView commitCourseGeneration(String courseId, String draftMarkdown, String wechatMarkdown) {
    CourseShellView courseShell = shell.ensureCourseShell(courseId);
    String draftId = courseShell.getDraftArticleId();
    String wechatId = courseShell.getWechatArticleId();
    LocalDateTime now = LocalDateTime.now();

    ArticleEntity draftArticle = support.requireArticle(draftId);
    ArticleEntity wechatArticle = support.requireArticle(wechatId);
    support.overlayVersionIdentityFromDocuments(draftArticle);
    support.overlayVersionIdentityFromDocuments(wechatArticle);
    int sharedVersionNo = Math.max(
        draftArticle.getCurrentVersionNo() == null ? 0 : draftArticle.getCurrentVersionNo(),
        wechatArticle.getCurrentVersionNo() == null ? 0 : wechatArticle.getCurrentVersionNo()) + 1;

    ArticleEntity draft = applyMarkdownWithVersion(
        draftArticle, draftMarkdown, now, sharedVersionNo, "ai_generation", "Content generation write");
    ArticleEntity wechat = applyMarkdownWithVersion(
        wechatArticle, wechatMarkdown, now, sharedVersionNo, "ai_generation", "Content generation write");
    // 并行镜像到 content_documents
    mirrorCourseArticle(draft, courseId, sharedVersionNo, "ai_generation", "Content generation write");
    mirrorCourseArticle(wechat, courseId, sharedVersionNo, "ai_generation", "Content generation write");

    CourseEntity course = courseStore.selectById(courseId);
    course.setStatus("generated");
    course.setProgress(100);
    course.setUpdatedAt(now);
    courseStore.updateById(course);

    return CourseGenerationCommitView.builder()
        .courseId(courseId)
        .draft(toArticleViewPreferringDocuments(draft))
        .wechat(toArticleViewPreferringDocuments(wechat))
        .partial(false)
        .build();
  }

  /**
   * 转文章视图：若 content_documents 有正文则覆盖旧表字段。
   *
   * @param article 文章实体
   * @return 视图
   */
  public ArticleView toArticleViewPreferringDocuments(ArticleEntity article) {
    overlayBodyFromDocuments(article);
    return views.articleView(article);
  }

  /**
   * 用统一文档表最新正文覆盖实体（无文档则保留旧表）。
   *
   * @param article 文章实体
   */
  public void overlayBodyFromDocuments(ArticleEntity article) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return;
    }
    String body = workContents.readCurrent(article.getId());
    if (StrUtil.isBlank(body)) {
      return;
    }
    article.setContent(body);
    article.setContentHash(ContentEntityUtil.sha256(body));
    article.setWordCount(body.length());
  }

  /**
   * 将课程文章正文追加写入 work_outputs 版本。
   *
   * @param article   已更新的文章实体
   * @param courseId  课程 id（未用，保留调用方签名）
   * @param versionNo 期望版本号（由 append 自增，忽略对齐）
   * @param source    来源
   * @param reason    原因
   */
  public void mirrorCourseArticle(
      ArticleEntity article, String courseId, int versionNo, String source, String reason) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return;
    }
    if (StrUtil.isNotBlank(courseId) || StrUtil.isNotBlank(article.getCourseId())) {
      String workId = StrUtil.blankToDefault(courseId, article.getCourseId());
      workContents.ensureOutput(
          article.getId(),
          workId,
          CourseOutputChannels.channelCode(article.getKind()),
          article.getTitle(),
          article.getStatus());
    }
    int written = workContents.appendVersion(
        article.getId(), article.getContent(), source, reason, null);
    article.setCurrentVersionNo(written);
    support.overlayVersionIdentityFromDocuments(article);
  }

  /**
   * 回写产出壳标题与状态。
   *
   * @param article 文章投影
   */
  private void syncOutputShell(ArticleEntity article) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return;
    }
    WorkOutputEntity output = workOutputMapper.selectById(article.getId());
    if (output == null) {
      return;
    }
    boolean dirty = false;
    if (StrUtil.isNotBlank(article.getTitle()) && !article.getTitle().equals(output.getTitle())) {
      output.setTitle(article.getTitle());
      dirty = true;
    }
    if (StrUtil.isNotBlank(article.getStatus()) && !article.getStatus().equals(output.getStatus())) {
      output.setStatus(article.getStatus());
      dirty = true;
    }
    if (dirty) {
      output.setUpdatedAt(LocalDateTime.now());
      workOutputMapper.updateById(output);
    }
  }

  /**
   * 写入正文并用指定版本号插入快照（同一次生成的原稿与公众号共用 version_no）。
   *
   * @param article   文章实体
   * @param markdown  Markdown 正文
   * @param now       时间戳
   * @param versionNo 版本号
   * @param source    来源
   * @param reason    原因
   * @return 已就地更新的文章实体
   */
  public ArticleEntity applyMarkdownWithVersion(
      ArticleEntity article,
      String markdown,
      LocalDateTime now,
      int versionNo,
      String source,
      String reason) {
    String content = markdown == null ? "" : markdown;
    // 内存更新元数据；正文 sole write 由后续 mirrorCourseArticle 落 content_documents
    article.setContent(content);
    article.setWordCount(content.length());
    article.setContentHash(ContentEntityUtil.sha256(content));
    article.setStatus("ready");
    article.setVersionToken((article.getVersionToken() == null ? 0 : article.getVersionToken()) + 1);
    article.setUpdatedAt(now);
    article.setCurrentVersionNo(versionNo);
    return article;
  }

  /**
   * 按文章类型写入生成结果；articleId 也可传 courseId 再按 kind 解析目标文章。
   *
   * @param articleId 文章 id，或课程 id（文章不存在时按课壳解析）
   * @param kind      期望 kind；可与文章现有 kind 校验
   * @param markdown  生成正文
   * @return 写入后的文章视图
   * @throws BusinessException id 空、kind 不匹配或目标文章不存在时
   */
  @Transactional
  public ArticleView generateArticleKind(String articleId, String kind, String markdown) {
    visibility.assertThemeActiveForArticleId(articleId);
    visibility.assertThemeActiveForCourseId(articleId);
    if (articleId == null || articleId.isBlank()) {
      throw new BusinessException("article_id_required", "articleId 不能为空", 400);
    }
    LocalDateTime now = LocalDateTime.now();
    ArticleEntity article = support.findArticleOrNull(articleId);
    if (article == null) {
      // 把 articleId 当作 courseId：建壳后按 kind 选草稿或公众号文章
      shell.ensureCourseShell(articleId);
      CourseEntity course = courseStore.selectById(articleId);
      String targetId = ArticleKind.WECHAT_ARTICLE.getValue().equals(kind)
          ? course.getCurrentWechatDocumentId()
          : course.getCurrentDraftDocumentId();
      article = support.requireArticle(targetId);
    } else if (kind != null && !kind.isBlank() && article.getKind() != null && !kind.equals(article.getKind())) {
      throw new BusinessException("article_kind_mismatch", "期望 kind=" + kind, 400);
    }
    applyMarkdown(article, markdown, now);
    if (kind != null && !kind.isBlank()) {
      article.setKind(kind);
    }
    return toArticleViewPreferringDocuments(article);
  }

  /**
   * 将 Markdown 写入文章字段并插入 generation 版本。
   *
   * @param article  目标文章（须已存在）
   * @param markdown 正文；null 按空串
   * @param now      时间戳
   * @return 已就地更新的文章实体
   */
  public ArticleEntity applyMarkdown(ArticleEntity article, String markdown, LocalDateTime now) {
    // 写正文并推进 token，再记 generation 版本
    String content = markdown == null ? "" : markdown;
    article.setContent(content);
    article.setWordCount(content.length());
    article.setContentHash(ContentEntityUtil.sha256(content));
    article.setStatus("ready");
    article.setVersionToken((article.getVersionToken() == null ? 0 : article.getVersionToken()) + 1);
    article.setUpdatedAt(now);
    insertContentVersion(article, "generation", "Content generation write", now);
    return article;
  }

  /**
   * 插入一条内容版本并回写 article.currentVersionNo。
   *
   * @param article 当前文章（读 content/hash）
   * @param source  版本来源码
   * @param reason  变更原因说明
   * @param now     创建时间
   * @return 新建版本实体
   */
  public ArticleVersionEntity insertContentVersion(
      ArticleEntity article, String source, String reason, LocalDateTime now) {
    int nextNo = (article.getCurrentVersionNo() == null ? 0 : article.getCurrentVersionNo()) + 1;
    article.setCurrentVersionNo(nextNo);
    mirrorCourseArticle(article, article.getCourseId(), nextNo, source, reason);
    WorkOutputVersionEntity version = findWorkOutputVersion(article.getId(), article.getCurrentVersionNo());
    if (version != null) {
      return toLegacyVersionShape(version, article.getId());
    }
    ArticleVersionEntity fallback = new ArticleVersionEntity();
    fallback.setId(IdsUtil.newId());
    fallback.setArticleId(article.getId());
    fallback.setVersionNo(article.getCurrentVersionNo());
    fallback.setContent(article.getContent() == null ? "" : article.getContent());
    fallback.setContentHash(article.getContentHash());
    fallback.setSource(source);
    fallback.setReason(reason);
    fallback.setCreatedAt(now);
    return fallback;
  }

  /**
   * 版本行投影为旧 ArticleVersionEntity 形状（供审核等仍依赖该类型的调用方）。
   *
   * @param version   版本行
   * @param articleId 文章 id
   * @return 投影实体
   */
  private ArticleVersionEntity toLegacyVersionShape(
      WorkOutputVersionEntity version, String articleId) {
    ArticleVersionEntity legacy = new ArticleVersionEntity();
    legacy.setId(version.getId());
    legacy.setArticleId(articleId);
    legacy.setVersionNo(version.getVersionNo());
    legacy.setContent(workContents.readVersion(version.getOutputId(), version.getVersionNo()));
    legacy.setContentHash(version.getContentHash());
    legacy.setSource(version.getSource());
    legacy.setReason(version.getReason());
    legacy.setWorkflowRunId(version.getWorkflowRunId());
    legacy.setCreatedAt(version.getCreatedAt());
    return legacy;
  }

  /**
   * 按版本号查找产出版本行。
   *
   * @param outputId  产出 id
   * @param versionNo 版本号
   * @return 版本行；缺失为 null
   */
  private WorkOutputVersionEntity findWorkOutputVersion(String outputId, int versionNo) {
    if (StrUtil.isBlank(outputId) || versionNo <= 0) {
      return null;
    }
    return workOutputVersionMapper.selectOne(new LambdaQueryWrapper<WorkOutputVersionEntity>()
        .eq(WorkOutputVersionEntity::getOutputId, outputId)
        .eq(WorkOutputVersionEntity::getVersionNo, versionNo)
        .last("LIMIT 1"));
  }
}
