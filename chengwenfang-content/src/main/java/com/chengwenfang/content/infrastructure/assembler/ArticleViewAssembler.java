package com.chengwenfang.content.infrastructure.assembler;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.application.ContentEntityUtil;
import com.chengwenfang.content.application.WorkContentStoreImpl;
import com.chengwenfang.content.domain.entity.ArticleEntity;
import com.chengwenfang.content.domain.entity.ArticleVersionEntity;
import com.chengwenfang.content.domain.entity.WorkOutputEntity;
import com.chengwenfang.content.facade.dto.view.ArticleVersionAuditView;
import com.chengwenfang.content.facade.dto.view.ArticleVersionView;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 文章与版本视图组装。
 */
@Component
@RequiredArgsConstructor
public class ArticleViewAssembler {

  /** 产出表。 */
  private final WorkOutputMapper workOutputMapper;
  /** 正文存储。 */
  private final WorkContentStoreImpl workContents;

  /**
   * 将文章实体转为对外视图。
   *
   * @param article 文章实体
   * @return 文章视图
   */
  public ArticleView articleView(ArticleEntity article) {
    String contentBody = article.getContent();
    if (contentBody == null && StrUtil.isNotBlank(article.getId())) {
      WorkOutputEntity output = workOutputMapper.selectById(article.getId());
      if (output != null) {
        contentBody = workContents.readCurrent(article.getId());
      }
    }
    if (contentBody == null) {
      contentBody = "";
    }
    String documentId = article.getId();
    WorkOutputEntity meta = findCourseOutput(article);
    String title = meta != null && StrUtil.isNotBlank(meta.getTitle())
        ? meta.getTitle()
        : article.getTitle();
    String status = meta != null && StrUtil.isNotBlank(meta.getStatus())
        ? meta.getStatus()
        : article.getStatus();
    Long versionToken = meta != null && meta.getVersionToken() != null
        ? meta.getVersionToken()
        : article.getVersionToken();
    Integer currentVersionNo = meta != null && meta.getCurrentVersionNo() != null
        ? meta.getCurrentVersionNo()
        : article.getCurrentVersionNo();
    String contentHash = meta != null && StrUtil.isNotBlank(meta.getContentHash())
        ? meta.getContentHash()
        : (article.getContentHash() == null ? ContentEntityUtil.sha256(contentBody) : article.getContentHash());
    Integer wordCount = meta != null && meta.getWordCount() != null
        ? meta.getWordCount()
        : (article.getWordCount() == null ? contentBody.length() : article.getWordCount());
    return ArticleView.builder()
        .id(article.getId())
        .documentId(documentId)
        .courseId(article.getCourseId())
        .kind(article.getKind())
        .title(title)
        .content(contentBody)
        .contentHash(contentHash)
        .wordCount(wordCount)
        .status(status)
        .versionToken(versionToken)
        .currentVersionNo(currentVersionNo)
        .updatedAt(article.getUpdatedAt() == null ? null : article.getUpdatedAt().toString())
        .build();
  }

  /**
   * 将版本实体转为视图（audit 占位为 0）。
   *
   * @param version 版本实体
   * @return 版本视图
   */
  public ArticleVersionView versionView(ArticleVersionEntity version) {
    // 审计计数暂占位为 0，待审计模块回填
    return ArticleVersionView.builder()
        .id(version.getId())
        .articleId(version.getArticleId())
        .versionNo(version.getVersionNo())
        .content(version.getContent())
        .contentHash(version.getContentHash())
        .source(version.getSource())
        .reason(version.getReason() == null ? "" : version.getReason())
        .workflowRunId(version.getWorkflowRunId())
        .auditRunId(version.getAuditRunId())
        .audit(ArticleVersionAuditView.builder().score(0).passed(0).warnings(0).failed(0).build())
        .createdAt(version.getCreatedAt() == null ? null : version.getCreatedAt().toString())
        .build();
  }

  /**
   * 读取课程产出壳行（title/status/version）。
   *
   * @param article 文章实体
   * @return 产出实体；缺失为 null
   */
  public WorkOutputEntity findCourseOutput(ArticleEntity article) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return null;
    }
    return workOutputMapper.selectById(article.getId());
  }
}
