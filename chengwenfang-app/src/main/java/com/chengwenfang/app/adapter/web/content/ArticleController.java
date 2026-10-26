package com.chengwenfang.app.adapter.web.content;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.FlexiblePayload;
import com.chengwenfang.content.facade.enums.ArticleKind;
import com.chengwenfang.content.facade.dto.request.ArticleUpdateRequest;
import com.chengwenfang.content.facade.dto.view.ArticleVersionView;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.GeneratedImageView;
import com.chengwenfang.content.facade.dto.view.HumanReviewImportEntry;
import com.chengwenfang.content.facade.dto.request.HumanReviewRequest;
import com.chengwenfang.content.facade.dto.view.HumanReviewView;
import com.chengwenfang.content.facade.dto.request.ImportHumanReviewsRequest;
import com.chengwenfang.content.facade.dto.view.ImportHumanReviewsView;
import com.chengwenfang.content.facade.dto.view.OkView;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.quality.application.review.CourseArticleAuditService;
import com.chengwenfang.quality.facade.AuditFacade;
import com.chengwenfang.quality.facade.dto.view.AuditResultView;
import com.chengwenfang.quality.facade.dto.request.AuditTextRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章、版本、人工审核与配图相关 HTTP 接口。
 */
@Tag(name = "文章", description = "文章详情、版本、审核修复与配图")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArticleController {
  /** 内容领域门面：文章 CRUD、审核、修复与配图。 */
  private final ContentFacade content;
  /** 任务门面：按 jobId 回读任务。 */
  private final JobFacade jobs;
  /** 确定性审核门面：对正文跑规则审计。 */
  private final AuditFacade audit;
  /** 课程文章审核编排：组装配图上下文。 */
  private final CourseArticleAuditService courseArticleAudits;

  /**
   * 获取文章详情。
   *
   * @param articleId 文章 id
   * @return 文章视图
   */
  @GetMapping("/articles/{articleId}")
  @Operation(summary = "获取文章详情")
  public ApiResult<ArticleView> article(@PathVariable String articleId) {
    return ApiResult.ok(content.getArticle(articleId));
  }

  /**
   * 列出文章历史版本（按版本号降序）。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   * @return 版本视图列表
   */
  @GetMapping("/articles/{articleId}/versions")
  @Operation(summary = "列出文章版本")
  public ApiResult<List<ArticleVersionView>> articleVersions(
      @PathVariable String articleId,
      @RequestParam(required = false) String documentId) {
    return ApiResult.ok(content.listArticleVersions(articleId, documentId));
  }

  /**
   * 将文章内容恢复到指定历史版本。
   *
   * @param articleId  文章 id
   * @param versionId  目标版本 id
   * @param documentId 统一文档 id；可空
   * @return 恢复后的文章视图
   */
  @PostMapping("/articles/{articleId}/restore/{versionId}")
  @Operation(summary = "恢复到历史版本")
  public ApiResult<ArticleView> restoreArticleVersion(
      @PathVariable String articleId,
      @PathVariable String versionId,
      @RequestParam(required = false) String documentId) {
    return ApiResult.ok(content.restoreArticleVersion(articleId, versionId, documentId));
  }

  /**
   * 获取文章最新人工审核记录。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   * @return 人工审核视图
   */
  @GetMapping({"/articles/{articleId}/human-review", "/hot-news/articles/{articleId}/human-review"})
  @Operation(summary = "获取人工审核记录")
  public ApiResult<HumanReviewView> getHumanReview(
      @PathVariable String articleId,
      @RequestParam(required = false) String documentId) {
    return ApiResult.ok(content.getHumanReview(articleId, documentId));
  }

  /**
   * 提交或更新人工审核（approved / rejected）。
   *
   * @param articleId 文章 id
   * @param body      审核请求；status 必填合法值；可含 documentId
   * @return 写入后的审核视图
   */
  @PutMapping({"/articles/{articleId}/human-review", "/hot-news/articles/{articleId}/human-review"})
  @Operation(summary = "提交或更新人工审核")
  public ApiResult<HumanReviewView> putHumanReview(
      @PathVariable String articleId, @Valid @RequestBody(required = false) HumanReviewRequest body) {
    return ApiResult.ok(content.putHumanReview(articleId, body));
  }

  /**
   * 删除该文章全部人工审核记录。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   * @return {@code ok=true} 表示删除完成
   */
  @DeleteMapping({"/articles/{articleId}/human-review", "/hot-news/articles/{articleId}/human-review"})
  @Operation(summary = "删除人工审核记录")
  public ApiResult<OkView> deleteHumanReview(
      @PathVariable String articleId,
      @RequestParam(required = false) String documentId) {
    content.deleteHumanReview(articleId, documentId);
    return ApiResult.ok(OkView.builder().ok(true).build());
  }

  /**
   * 批量迁移历史人工审核到公众号文章。
   *
   * @param body 导入条目列表；空或 null 视为无条目
   * @return 导入统计（成功/跳过 key）
   */
  @PostMapping("/migrations/human-reviews")
  @Operation(summary = "迁移人工审核历史数据")
  public ApiResult<ImportHumanReviewsView> migrateHumanReviews(
      @Valid @RequestBody(required = false) ImportHumanReviewsRequest body) {
    // 空请求按空列表，避免 NPE
    List<HumanReviewImportEntry> entries = body == null || body.getEntries() == null
        ? List.of()
        : body.getEntries();
    return ApiResult.ok(content.importHumanReviews(entries));
  }

  /**
   * 更新文章标题/正文（含 versionToken 乐观锁）。
   *
   * @param articleId 文章 id
   * @param body      更新请求
   * @return 更新后的文章视图
   */
  @PutMapping("/articles/{articleId}")
  @Operation(summary = "更新文章标题/正文")
  public ApiResult<ArticleView> updateArticle(
      @PathVariable String articleId, @Valid @RequestBody ArticleUpdateRequest body) {
    return ApiResult.ok(content.updateArticle(articleId, body));
  }

  /**
   * 按 kind 启动文章生成相关异步任务。
   *
   * @param articleId 文章或课程 id（作为 entityId）
   * @param kind      文章类型或原始任务 type；course_draft / wechat_article 会映射到标准 JobTypes
   * @param body      可选任务入参
   * @return 新建任务视图
   */
  @PostMapping("/articles/{articleId}/generation/{kind}")
  @Operation(summary = "启动文章生成相关任务")
  public ApiResult<JobView> articleGeneration(
      @PathVariable String articleId,
      @PathVariable String kind,
      @Valid @RequestBody(required = false) FlexiblePayload body) {
    // 将文章 kind 映射为任务类型；未知 kind 原样作为 type
    String type;
    if (ArticleKind.COURSE_DRAFT.getValue().equals(kind)) {
      type = JobTypes.COURSE_DRAFT_GENERATION;
    } else if (ArticleKind.WECHAT_ARTICLE.getValue().equals(kind)) {
      type = JobTypes.WECHAT_ARTICLE_GENERATION;
    } else {
      type = kind;
    }
    FlexiblePayload payload = FlexiblePayload.ofNullable(body);
    payload.putIfAbsent("articleId", articleId);
    // 显式 documentId 优先；课程轨通常等于 articleId
    Object rawDocumentId = payload.asMap().get("documentId");
    String explicitDocumentId = rawDocumentId == null ? null : String.valueOf(rawDocumentId);
    if (explicitDocumentId == null || explicitDocumentId.isBlank() || "null".equals(explicitDocumentId)) {
      payload.put("documentId", articleId);
    }
    String jobId = content.createGenerationJob(type, articleId, payload);
    return ApiResult.ok(jobs.get(jobId));
  }

  /**
   * 对指定文章正文执行确定性审核。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   * @return 审核结果视图
   */
  @PostMapping("/articles/{articleId}/audit")
  @Operation(summary = "对文章执行确定性审核")
  public ApiResult<AuditResultView> auditArticle(
      @PathVariable String articleId,
      @RequestParam(required = false) String documentId) {
    // 带课程配图上下文审核，避免 IMAGE_ASSETS_* 因空资产误报
    return ApiResult.ok(courseArticleAudits.auditArticle(articleId, documentId));
  }

  /**
   * 对任意正文文本执行确定性审核（不绑定文章 id）。
   *
   * @param body 含 {@code text} 的请求；空则按空串审核
   * @return 审核结果视图
   */
  @PostMapping("/articles/audit-text")
  @Operation(summary = "对正文执行确定性审核")
  public ApiResult<AuditResultView> audit(@Valid @RequestBody(required = false) AuditTextRequest body) {
    String text = body == null || body.getText() == null ? "" : body.getText();
    return ApiResult.ok(audit.auditText(text));
  }

  /**
   * 启动文章 AI 修复异步任务。
   *
   * @param articleId 文章 id
   * @param body      可选入参（可含 documentId）
   * @return 新建修复任务视图
   */
  @PostMapping("/articles/{articleId}/repair")
  @Operation(summary = "启动文章 AI 修复任务")
  public ApiResult<JobView> repairArticle(
      @PathVariable String articleId,
      @Valid @RequestBody(required = false) FlexiblePayload body) {
    FlexiblePayload payload = FlexiblePayload.ofNullable(body);
    Object rawDocumentId = payload.asMap().get("documentId");
    String documentId = rawDocumentId == null ? null : String.valueOf(rawDocumentId);
    String jobId = content.startRepair(articleId, documentId);
    return ApiResult.ok(jobs.get(jobId));
  }

  /**
   * 应用夹具类元数据修复后，对修复结果再跑确定性审核。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   * @return 修复后正文的审核结果
   */
  @PostMapping("/articles/{articleId}/repair-metadata")
  @Operation(summary = "修复文章元数据")
  public ApiResult<AuditResultView> repairArticleMetadata(
      @PathVariable String articleId,
      @RequestParam(required = false) String documentId) {
    // 绑定配图 articleId 后复审
    return ApiResult.ok(courseArticleAudits.repairMetadataAndAudit(articleId, documentId));
  }

  /**
   * 入队文章配图生成任务。
   *
   * @param articleId 文章 id
   * @param body      可选入参（会自动补 articleId / documentId）
   * @return 新建配图任务视图
   */
  @PostMapping("/articles/{articleId}/images")
  @Operation(summary = "入队文章配图任务")
  public ApiResult<JobView> enqueueArticleImage(
      @PathVariable String articleId, @Valid @RequestBody(required = false) FlexiblePayload body) {
    FlexiblePayload payload = FlexiblePayload.ofNullable(body);
    String jobId = content.enqueueImageJob(articleId, payload);
    return ApiResult.ok(jobs.get(jobId));
  }

  /**
   * 列出文章已生成配图元数据。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；空则服务端解析
   * @return 配图视图列表
   */
  @GetMapping({"/articles/{articleId}/images", "/hot-news/articles/{articleId}/images"})
  @Operation(summary = "列出文章配图")
  public ApiResult<List<GeneratedImageView>> listArticleImages(
      @PathVariable String articleId,
      @RequestParam(required = false) String documentId) {
    return ApiResult.ok(content.listImages(articleId, documentId));
  }

  /**
   * 按 imageId 返回配图二进制（PNG），带短期缓存头。
   *
   * @param imageId 配图 id
   * @return 文件资源响应
   */
  @GetMapping("/images/{imageId}")
  @Operation(summary = "获取配图详情")
  public ResponseEntity<Resource> getImage(@PathVariable String imageId) {
    // 解析本地路径后以 FileSystemResource 流式返回
    Path path = content.getImageFile(imageId);
    FileSystemResource resource = new FileSystemResource(path);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
        .contentType(MediaType.IMAGE_PNG)
        .body(resource);
  }

  /**
   * 删除配图记录及文件（尽力清理）。
   *
   * @param imageId 配图 id
   * @return 空成功体
   */
  @DeleteMapping("/images/{imageId}")
  @Operation(summary = "删除配图")
  public ApiResult<Void> deleteImage(@PathVariable String imageId) {
    content.deleteImage(imageId);
    return ApiResult.ok();
  }
}
