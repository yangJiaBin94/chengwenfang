package com.chengwenfang.app.adapter.web.content;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.app.adapter.web.hotnews.HotNewsGenerationInputEnricher;
import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.FlexiblePayload;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.constant.OutputTrackKinds;
import com.chengwenfang.content.facade.dto.WorkOutputRef;
import com.chengwenfang.content.facade.dto.request.ArticleLayoutSaveRequest;
import com.chengwenfang.content.facade.dto.request.ArticleUpdateRequest;
import com.chengwenfang.content.facade.dto.request.HumanReviewRequest;
import com.chengwenfang.content.facade.dto.view.ArticleLayoutView;
import com.chengwenfang.content.facade.dto.view.ArticleVersionView;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.dto.view.GeneratedImageView;
import com.chengwenfang.content.facade.dto.view.HumanReviewView;
import com.chengwenfang.content.facade.dto.view.OkView;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleContentUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsArticleUpdateRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsGenerationRunRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsGenerationSettingsRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleVersionView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.publishing.facade.PublishingFacade;
import com.chengwenfang.publishing.facade.dto.request.PublishingSnapshotCreateRequest;
import com.chengwenfang.publishing.facade.dto.view.PublishingSnapshotView;
import com.chengwenfang.quality.application.review.CourseArticleAuditService;
import com.chengwenfang.quality.application.review.HotArticleReviewService;
import com.chengwenfang.quality.facade.dto.view.AuditResultView;
import com.chengwenfang.quality.facade.dto.view.HotArticleAuditView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一文档并行路由：按产出 ID 查作品与模式后再分派；URL 不变。
 */
@Tag(name = "统一文档", description = "按产出 id 访问正文文档（并行于旧 article 路径）")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentsController {
  /** 内容门面：产出身份、课程文章与共享能力。 */
  private final ContentFacade content;
  /** 热点门面：热点文章与生成。 */
  private final HotNewsFacade hotNews;
  /** 发布门面：排版快照。 */
  private final PublishingFacade publishing;
  /** 任务门面：回读入队 Job。 */
  private final JobFacade jobs;
  /** 热点生成入参补全。 */
  private final HotNewsGenerationInputEnricher generationInputEnricher;
  /** 课程文章确定性审核编排。 */
  private final CourseArticleAuditService courseArticleAudits;
  /** 热点文章审核汇总。 */
  private final HotArticleReviewService hotArticleReviews;

  /**
   * 获取文档详情（课程 ArticleView / 热点 HotNewsArticleView）。
   *
   * @param documentId 产出 id
   * @return 对应域文章视图
   */
  @GetMapping("/documents/{documentId}")
  @Operation(summary = "获取统一文档详情")
  public ApiResult<?> getDocument(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    if (ref.isCourse()) {
      return ApiResult.ok(content.getArticle(ref.resolveArticleId()));
    }
    requireHotNews(ref);
    return ApiResult.ok(hotNews.getHotArticle(ref.resolveArticleId()));
  }

  /**
   * 更新文档正文/标题（课程走乐观锁；热点按 kind 更新 draft 或 wechat 轨）。
   *
   * @param documentId 产出 id
   * @param body       更新载荷（content / title / versionToken / baseContentHash 等）
   * @return 课程 ArticleView 或热点 HotNewsArticleView
   */
  @PutMapping("/documents/{documentId}")
  @Operation(summary = "更新统一文档正文")
  public ApiResult<?> updateDocument(
      @PathVariable String documentId,
      @RequestBody(required = false) FlexiblePayload body) {
    WorkOutputRef ref = requireRef(documentId);
    FlexiblePayload payload = FlexiblePayload.ofNullable(body);
    Map<String, Object> fields = payload.asMap();
    if (ref.isCourse()) {
      // 课程：委托现有 updateArticle（含 versionToken）
      ArticleUpdateRequest request = ArticleUpdateRequest.builder()
          .title(stringField(fields, "title"))
          .content(stringField(fields, "content"))
          .versionToken(Convert.toLong(fields.get("versionToken"), null))
          .source(stringField(fields, "source"))
          .reason(stringField(fields, "reason"))
          .build();
      return ApiResult.ok(content.updateArticle(ref.resolveArticleId(), request));
    }
    requireHotNews(ref);
    String text = stringField(fields, "content");
    String baseHash = stringField(fields, "baseContentHash");
    String reason = stringField(fields, "reason");
    // 草稿轨与公众号轨分路径更新；其它 kind 拒绝，避免误写 wechat
    if (OutputTrackKinds.HOT_DRAFT.equals(ref.getKind())) {
      HotNewsArticleContentUpdateRequest draftRequest = HotNewsArticleContentUpdateRequest.builder()
          .content(text)
          .baseContentHash(baseHash)
          .reason(reason)
          .build();
      return ApiResult.ok(hotNews.updateArticleContent(
          ref.resolveArticleId(), OutputTrackKinds.HOT_DRAFT, draftRequest));
    }
    if (OutputTrackKinds.HOT_WECHAT.equals(ref.getKind())) {
      HotNewsArticleUpdateRequest wechatRequest = HotNewsArticleUpdateRequest.builder()
          .content(text)
          .baseContentHash(baseHash)
          .reason(reason)
          .build();
      return ApiResult.ok(hotNews.updateHotArticle(ref.resolveArticleId(), wechatRequest));
    }
    throw new BusinessException(
        ErrorCodes.INVALID_CONTENT_KIND, "热点文档 kind 须为 draft 或 wechat", 400);
  }

  /**
   * 列出文档版本。
   *
   * @param documentId 产出 id
   * @return 版本列表（课程/热点视图类型不同）
   */
  @GetMapping("/documents/{documentId}/versions")
  @Operation(summary = "列出统一文档版本")
  public ApiResult<?> listVersions(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    if (ref.isCourse()) {
      List<ArticleVersionView> versions =
          content.listArticleVersions(ref.resolveArticleId(), documentId);
      return ApiResult.ok(versions);
    }
    requireHotNews(ref);
    List<HotNewsArticleVersionView> versions =
        hotNews.listArticleVersions(ref.resolveArticleId(), documentId);
    return ApiResult.ok(versions);
  }

  /**
   * 恢复课程文档到指定历史版本（热点不支持本路径）。
   *
   * @param documentId 产出 id
   * @param versionId  版本行 id
   * @return 恢复后的课程文章视图
   */
  @PostMapping("/documents/{documentId}/restore/{versionId}")
  @Operation(summary = "恢复统一文档到历史版本")
  public ApiResult<ArticleView> restoreVersion(
      @PathVariable String documentId, @PathVariable String versionId) {
    WorkOutputRef ref = requireRef(documentId);
    requireCourse(ref);
    return ApiResult.ok(content.restoreArticleVersion(ref.resolveArticleId(), versionId, documentId));
  }

  /**
   * 获取排版布局。
   *
   * @param documentId 产出 id
   * @return 布局视图
   */
  @GetMapping("/documents/{documentId}/layout")
  @Operation(summary = "获取统一文档排版布局")
  public ApiResult<ArticleLayoutView> getLayout(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    return ApiResult.ok(content.getOrInitLayout(ref.resolveArticleId(), documentId));
  }

  /**
   * 保存排版布局。
   *
   * @param documentId 产出 id
   * @param body       保存请求
   * @return 布局视图
   */
  @PutMapping("/documents/{documentId}/layout")
  @Operation(summary = "保存统一文档排版布局")
  public ApiResult<ArticleLayoutView> putLayout(
      @PathVariable String documentId,
      @Valid @RequestBody(required = false) ArticleLayoutSaveRequest body) {
    WorkOutputRef ref = requireRef(documentId);
    ArticleLayoutSaveRequest request = body == null ? new ArticleLayoutSaveRequest() : body;
    request.setDocumentId(documentId);
    return ApiResult.ok(content.saveLayout(ref.resolveArticleId(), request, documentId));
  }

  /**
   * 列出配图。
   *
   * @param documentId 产出 id
   * @return 配图列表
   */
  @GetMapping("/documents/{documentId}/images")
  @Operation(summary = "列出统一文档配图")
  public ApiResult<List<GeneratedImageView>> listImages(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    return ApiResult.ok(content.listImages(ref.resolveArticleId(), documentId));
  }

  /**
   * 入队配图任务。
   *
   * @param documentId 产出 id
   * @param body       可选入参
   * @return 新建任务
   */
  @PostMapping("/documents/{documentId}/images")
  @Operation(summary = "入队统一文档配图任务")
  public ApiResult<JobView> enqueueImages(
      @PathVariable String documentId,
      @Valid @RequestBody(required = false) FlexiblePayload body) {
    WorkOutputRef ref = requireRef(documentId);
    FlexiblePayload payload = FlexiblePayload.ofNullable(body);
    payload.put("documentId", documentId);
    payload.putIfAbsent("articleId", ref.resolveArticleId());
    String jobId = content.enqueueImageJob(ref.resolveArticleId(), payload);
    return ApiResult.ok(jobs.get(jobId));
  }

  /**
   * 获取人工审核。
   *
   * @param documentId 产出 id
   * @return 审核视图
   */
  @GetMapping("/documents/{documentId}/human-review")
  @Operation(summary = "获取统一文档人工审核")
  public ApiResult<HumanReviewView> getHumanReview(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    return ApiResult.ok(content.getHumanReview(ref.resolveArticleId(), documentId));
  }

  /**
   * 提交人工审核。
   *
   * @param documentId 产出 id
   * @param body       审核请求
   * @return 审核视图
   */
  @PutMapping("/documents/{documentId}/human-review")
  @Operation(summary = "提交统一文档人工审核")
  public ApiResult<HumanReviewView> putHumanReview(
      @PathVariable String documentId,
      @Valid @RequestBody(required = false) HumanReviewRequest body) {
    WorkOutputRef ref = requireRef(documentId);
    HumanReviewRequest request = body == null ? new HumanReviewRequest() : body;
    request.setDocumentId(documentId);
    return ApiResult.ok(content.putHumanReview(ref.resolveArticleId(), request));
  }

  /**
   * 删除人工审核。
   *
   * @param documentId 产出 id
   * @return ok
   */
  @DeleteMapping("/documents/{documentId}/human-review")
  @Operation(summary = "删除统一文档人工审核")
  public ApiResult<OkView> deleteHumanReview(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    content.deleteHumanReview(ref.resolveArticleId(), documentId);
    return ApiResult.ok(OkView.builder().ok(true).build());
  }

  /**
   * 创建发布排版快照。
   *
   * @param documentId 产出 id
   * @param body       可选快照参数
   * @return 快照视图
   */
  @PostMapping("/documents/{documentId}/publishing-snapshots")
  @Operation(summary = "创建统一文档发布快照")
  public ApiResult<PublishingSnapshotView> createPublishingSnapshot(
      @PathVariable String documentId,
      @Valid @RequestBody(required = false) PublishingSnapshotCreateRequest body) {
    WorkOutputRef ref = requireRef(documentId);
    String markdown = "";
    if (ref.isCourse()) {
      ArticleView article = content.getArticle(ref.resolveArticleId());
      markdown = article.getContent() == null ? "" : article.getContent();
    } else {
      requireHotNews(ref);
      HotNewsArticleView article = hotNews.getHotArticle(ref.resolveArticleId());
      markdown = article.getContent() == null ? "" : article.getContent();
    }
    if (body != null && body.getMarkdown() != null) {
      markdown = body.getMarkdown();
    }
    PublishingSnapshotCreateRequest request =
        body == null ? new PublishingSnapshotCreateRequest() : body;
    request.setDocumentId(documentId);
    return ApiResult.ok(publishing.createSnapshot(ref.resolveArticleId(), markdown, request));
  }

  /**
   * 列出统一文档发布排版快照。
   *
   * @param documentId 产出 id
   * @return 快照列表
   */
  @GetMapping("/documents/{documentId}/publishing-snapshots")
  @Operation(summary = "列出统一文档发布快照")
  public ApiResult<List<PublishingSnapshotView>> listPublishingSnapshots(
      @PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    return ApiResult.ok(publishing.listSnapshots(ref.resolveArticleId(), documentId));
  }

  /**
   * 更新热点文档生成设置。
   *
   * @param documentId 产出 id（热点）
   * @param body       生成设置
   * @return 热点文章视图
   */
  @PatchMapping("/documents/{documentId}/generation-settings")
  @Operation(summary = "更新热点统一文档生成设置")
  public ApiResult<HotNewsArticleView> patchGenerationSettings(
      @PathVariable String documentId,
      @Valid @RequestBody(required = false) HotNewsGenerationSettingsRequest body) {
    WorkOutputRef ref = requireRef(documentId);
    requireHotNews(ref);
    HotNewsGenerationSettingsRequest request =
        body == null ? new HotNewsGenerationSettingsRequest() : body;
    request.setDocumentId(documentId);
    return ApiResult.ok(hotNews.updateGenerationSettings(ref.resolveArticleId(), request));
  }

  /**
   * 启动热点文档生成运行。
   *
   * @param documentId 产出 id（热点）
   * @param body       运行请求
   * @return 生成 Job
   */
  @PostMapping("/documents/{documentId}/generation-runs")
  @Operation(summary = "启动热点统一文档生成运行")
  public ApiResult<JobView> startGenerationRun(
      @PathVariable String documentId,
      @Valid @RequestBody(required = false) HotNewsGenerationRunRequest body) {
    WorkOutputRef ref = requireRef(documentId);
    requireHotNews(ref);
    HotNewsGenerationRunRequest request =
        body == null ? new HotNewsGenerationRunRequest() : body;
    request.setDocumentId(documentId);
    Map<String, Object> input = generationInputEnricher.enrich(request);
    return ApiResult.ok(hotNews.startGenerationRun(ref.resolveArticleId(), input));
  }

  /**
   * 列出热点文档生成运行。
   *
   * @param documentId 产出 id（热点）
   * @return Job 列表
   */
  @GetMapping("/documents/{documentId}/generation-runs")
  @Operation(summary = "列出热点统一文档生成运行")
  public ApiResult<List<JobView>> listGenerationRuns(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    requireHotNews(ref);
    return ApiResult.ok(hotNews.listGenerationRuns(ref.resolveArticleId()));
  }

  /**
   * 对课程文档执行确定性审核。
   *
   * @param documentId 产出 id（课程）
   * @return 审核结果
   */
  @PostMapping("/documents/{documentId}/audit")
  @Operation(summary = "对课程统一文档执行确定性审核")
  public ApiResult<AuditResultView> auditDocument(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    requireCourse(ref);
    return ApiResult.ok(courseArticleAudits.auditArticle(ref.resolveArticleId(), documentId));
  }

  /**
   * 启动课程文档 AI 修复任务。
   *
   * @param documentId 产出 id（课程）
   * @param body       可选入参
   * @return 修复 Job
   */
  @PostMapping("/documents/{documentId}/repair")
  @Operation(summary = "启动课程统一文档 AI 修复")
  public ApiResult<JobView> repairDocument(
      @PathVariable String documentId,
      @Valid @RequestBody(required = false) FlexiblePayload body) {
    WorkOutputRef ref = requireRef(documentId);
    requireCourse(ref);
    String jobId = content.startRepair(ref.resolveArticleId(), documentId);
    return ApiResult.ok(jobs.get(jobId));
  }

  /**
   * 课程文档元数据修复后复审。
   *
   * @param documentId 产出 id（课程）
   * @return 复审结果
   */
  @PostMapping("/documents/{documentId}/repair-metadata")
  @Operation(summary = "修复课程统一文档元数据并复审")
  public ApiResult<AuditResultView> repairDocumentMetadata(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    requireCourse(ref);
    return ApiResult.ok(
        courseArticleAudits.repairMetadataAndAudit(ref.resolveArticleId(), documentId));
  }

  /**
   * 获取热点文档最近一次审核汇总。
   *
   * @param documentId 产出 id（热点）
   * @return 审稿汇总
   */
  @GetMapping("/documents/{documentId}/hot-reviews/latest")
  @Operation(summary = "获取热点统一文档最新审核")
  public ApiResult<HotArticleAuditView> latestHotReview(@PathVariable String documentId) {
    WorkOutputRef ref = requireRef(documentId);
    requireHotNews(ref);
    return ApiResult.ok(hotArticleReviews.latest(ref.resolveArticleId(), documentId));
  }

  /**
   * 按产出 id 反查；缺失则 404。
   *
   * @param documentId 产出 id
   * @return 身份投影
   */
  private WorkOutputRef requireRef(String documentId) {
    WorkOutputRef ref = content.findOutputRef(documentId);
    if (ref == null || StrUtil.isBlank(ref.getWorkId())) {
      throw new BusinessException(ErrorCodes.DOCUMENT_NOT_FOUND, "文档不存在", 404);
    }
    return ref;
  }

  /**
   * 从载荷取字符串字段；缺失或 JSON null 字面量视为 null。
   *
   * @param fields 载荷 map
   * @param key    字段名
   * @return 字符串；可空
   */
  private static String stringField(Map<String, Object> fields, String key) {
    if (fields == null || !fields.containsKey(key) || fields.get(key) == null) {
      return null;
    }
    String value = String.valueOf(fields.get(key));
    return "null".equals(value) ? null : value;
  }

  /**
   * 断言课程产出。
   *
   * @param ref 产出身份
   */
  private static void requireCourse(WorkOutputRef ref) {
    if (!ref.isCourse()) {
      throw new BusinessException(
          ErrorCodes.DOCUMENT_DOMAIN_MISMATCH, "该接口仅支持课程文档", 400);
    }
  }

  /**
   * 断言热点产出。
   *
   * @param ref 产出身份
   */
  private static void requireHotNews(WorkOutputRef ref) {
    if (!ref.isHotNews()) {
      throw new BusinessException(
          ErrorCodes.DOCUMENT_DOMAIN_MISMATCH, "该接口仅支持热点文档", 400);
    }
  }
}
