package com.chengwenfang.app.adapter.web.content;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.request.ArticleLayoutSaveRequest;
import com.chengwenfang.content.facade.dto.view.ArticleLayoutView;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.dto.view.OkView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.publishing.facade.PublishingFacade;
import com.chengwenfang.publishing.facade.dto.request.PublishingRenderRequest;
import com.chengwenfang.publishing.facade.dto.view.PublishingRenderView;
import com.chengwenfang.publishing.facade.dto.request.PublishingSnapshotCreateRequest;
import com.chengwenfang.publishing.facade.dto.view.PublishingSnapshotView;
import com.chengwenfang.publishing.facade.dto.request.LayoutThemeSaveRequest;
import com.chengwenfang.publishing.facade.dto.view.LayoutThemeView;
import com.chengwenfang.publishing.facade.dto.request.ThemeStatusUpdateRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章排版布局与发布主题相关 HTTP 接口。
 */
@Tag(name = "排版", description = "布局、发布快照与排版主题")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PublishingController {
  /** 内容门面：课程文章布局读写与产出解析。 */
  private final ContentFacade content;
  /** 热点门面：创建快照时回退读取热点文章正文。 */
  private final HotNewsFacade hotNews;
  /** 排版/发布门面：快照、主题与渲染。 */
  private final PublishingFacade publishing;

  /**
   * 获取文章排版布局；不存在则初始化空配置。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   * @return 布局视图
   */
  @GetMapping("/articles/{articleId}/layout")
  @Operation(summary = "获取文章排版布局")
  public ApiResult<ArticleLayoutView> getArticleLayout(
      @PathVariable String articleId,
      @RequestParam(required = false) String documentId) {
    return ApiResult.ok(content.getOrInitLayout(articleId, documentId));
  }

  /**
   * 保存文章排版布局（主题版本与选项）。
   *
   * @param articleId 文章 id
   * @param body      保存请求；可为 null（可含 documentId）
   * @return 保存后的布局视图
   */
  @PutMapping("/articles/{articleId}/layout")
  @Operation(summary = "保存文章排版布局")
  public ApiResult<ArticleLayoutView> putArticleLayout(
      @PathVariable String articleId, @Valid @RequestBody(required = false) ArticleLayoutSaveRequest body) {
    String documentId = body == null ? null : body.getDocumentId();
    return ApiResult.ok(content.saveLayout(articleId, body, documentId));
  }

  /**
   * 创建发布排版快照：优先读课程文章，失败则读热点文章；body.markdown 可覆盖。
   *
   * @param articleId 文章 id（课程或热点）
   * @param body      可选快照创建参数；可带覆盖用 markdown
   * @return 新建快照视图
   */
  @PostMapping({
      "/articles/{articleId}/publishing-snapshots",
      "/hot-news/articles/{articleId}/publishing-snapshots"
  })
  @Operation(summary = "创建发布排版快照")
  public ApiResult<PublishingSnapshotView> createPublishingSnapshot(
      @PathVariable String articleId, @Valid @RequestBody(required = false) PublishingSnapshotCreateRequest body) {
    String markdown = "";
    try {
      // 先按内容域文章取正文
      ArticleView article = content.getArticle(articleId);
      markdown = article.getContent() == null ? "" : article.getContent();
    } catch (Exception ex) {
      // 非课程文章时回退到热点文章
      var article = hotNews.getHotArticle(articleId);
      markdown = article.getContent() == null ? "" : article.getContent();
    }
    // 请求体显式 markdown 优先生效
    if (body != null && body.getMarkdown() != null) {
      markdown = body.getMarkdown();
    }
    PublishingSnapshotCreateRequest request = body == null ? new PublishingSnapshotCreateRequest() : body;
    if (request.getDocumentId() == null || request.getDocumentId().isBlank()) {
      request.setDocumentId(content.resolvePublishingOutputId(articleId));
    }
    return ApiResult.ok(publishing.createSnapshot(articleId, markdown, request));
  }

  /**
   * 列出文章发布排版快照。
   *
   * @param articleId 文章 id
   * @return 快照列表
   */
  @GetMapping({
      "/articles/{articleId}/publishing-snapshots",
      "/hot-news/articles/{articleId}/publishing-snapshots"
  })
  @Operation(summary = "列出发布快照")
  public ApiResult<List<PublishingSnapshotView>> listPublishingSnapshots(@PathVariable String articleId) {
    return ApiResult.ok(publishing.listSnapshots(
        articleId, content.resolvePublishingOutputId(articleId)));
  }

  /**
   * 列出全部排版主题。
   *
   * @return 主题视图列表
   */
  @GetMapping("/layout/themes")
  @Operation(summary = "列出排版主题")
  public ApiResult<List<LayoutThemeView>> layoutThemes() {
    return ApiResult.ok(publishing.listThemes());
  }

  /**
   * 创建排版主题。
   *
   * @param body 主题保存请求；可为 null
   * @return 新建主题视图
   */
  @PostMapping("/layout/themes")
  @Operation(summary = "创建排版主题")
  public ApiResult<LayoutThemeView> createLayoutTheme(
      @Valid @RequestBody(required = false) LayoutThemeSaveRequest body) {
    return ApiResult.ok(publishing.createTheme(body));
  }

  /**
   * 更新排版主题。
   *
   * @param id   主题 id
   * @param body 更新请求；可为 null
   * @return 更新后的主题视图
   */
  @PutMapping("/layout/themes/{id}")
  @Operation(summary = "更新排版主题")
  public ApiResult<LayoutThemeView> updateLayoutTheme(
      @PathVariable String id, @Valid @RequestBody(required = false) LayoutThemeSaveRequest body) {
    return ApiResult.ok(publishing.updateTheme(id, body));
  }

  /**
   * 删除排版主题。
   *
   * @param id 主题 id
   * @return {@code ok=true}
   */
  @DeleteMapping("/layout/themes/{id}")
  @Operation(summary = "删除排版主题")
  public ApiResult<OkView> deleteLayoutTheme(@PathVariable String id) {
    publishing.deleteTheme(id);
    return ApiResult.ok(OkView.builder().ok(true).build());
  }

  /**
   * 克隆排版主题。
   *
   * @param id 源主题 id
   * @return 克隆出的新主题视图
   */
  @PostMapping("/layout/themes/{id}/clone")
  @Operation(summary = "克隆排版主题")
  public ApiResult<LayoutThemeView> cloneLayoutTheme(@PathVariable String id) {
    return ApiResult.ok(publishing.cloneTheme(id));
  }

  /**
   * 设置主题启用状态。
   *
   * @param id   主题 id
   * @param body 含 status；空则默认 enabled
   * @return 更新后的主题视图
   */
  @PatchMapping("/layout/themes/{id}/status")
  @Operation(summary = "设置主题启用状态")
  public ApiResult<LayoutThemeView> setLayoutThemeStatus(
      @PathVariable String id, @Valid @RequestBody(required = false) ThemeStatusUpdateRequest body) {
    // 未传 status 时默认启用
    String status = body == null || body.getStatus() == null ? "enabled" : body.getStatus();
    return ApiResult.ok(publishing.setStatus(id, status));
  }

  /**
   * 按主题渲染 Markdown 为发布 HTML。
   *
   * @param body 渲染请求；themeVersionId 优先，否则用 themeKey
   * @return 渲染结果视图
   */
  @PostMapping("/publishing/render")
  @Operation(summary = "按主题渲染 Markdown")
  public ApiResult<PublishingRenderView> render(@Valid @RequestBody PublishingRenderRequest body) {
    PublishingRenderRequest input = body == null ? new PublishingRenderRequest() : body;
    // 优先 themeVersionId；空或字面 "null" 时回退 themeKey
    String themeRef = input.getThemeVersionId();
    if (themeRef == null || themeRef.isBlank() || "null".equals(themeRef)) {
      themeRef = input.getThemeKey();
    }
    return ApiResult.ok(publishing.renderMarkdown(
        input.getMarkdown() == null ? "" : input.getMarkdown(),
        themeRef));
  }
}
