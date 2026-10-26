package com.workbench.app.adapter.web.hotnews;

import com.workbench.common.ApiResult;
import com.workbench.mode.hotnews.facade.dto.request.HotNewsArticleContentUpdateRequest;
import com.workbench.mode.hotnews.facade.dto.request.HotNewsArticleUpdateRequest;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleVersionView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.mode.hotnews.facade.dto.request.HotNewsGenerationRunRequest;
import com.workbench.mode.hotnews.facade.dto.request.HotNewsGenerationSettingsRequest;
import com.workbench.mode.hotnews.facade.dto.request.HotNewsRewriteRequest;
import com.workbench.job.facade.dto.view.JobView;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 热点文章读写、生成与改写。 */
@Tag(name = "热点文章", description = "热点文章读写、生成与改写")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HotNewsArticleController {
  /** 热点资讯领域门面。 */
  private final HotNewsFacade hotNews;
  /** 成文任务入参补全（工作流 id）。 */
  private final HotNewsGenerationInputEnricher generationInputEnricher;

  /**
   * 入队热点文章 AI 改写任务。
   *
   * @param articleId 文章 ID
   * @param body      改写请求；可为 null
   * @return 已入队 Job
   */
  @PostMapping("/hot-news/articles/{articleId}/rewrite")
  @Operation(summary = "启动热点文章改写")
  public ApiResult<JobView> startHotArticleRewrite(
      @PathVariable String articleId,
      @Valid @RequestBody(required = false) HotNewsRewriteRequest body) {
    return ApiResult.ok(hotNews.startArticleRewrite(articleId, body));
  }

  /**
   * 更新热点文章正文。
   *
   * @param articleId 文章 ID
   * @param kind      正文类型（如 draft/wechat）
   * @param body      正文更新请求；可为 null
   * @return 更新后的文章视图
   */
  @PatchMapping("/hot-news/articles/{articleId}/contents/{kind}")
  @Operation(summary = "更新热点文章正文")
  public ApiResult<HotNewsArticleView> patchHotArticleContent(
      @PathVariable String articleId,
      @PathVariable String kind,
      @Valid @RequestBody(required = false) HotNewsArticleContentUpdateRequest body) {
    return ApiResult.ok(hotNews.updateArticleContent(articleId, kind, body));
  }

  /**
   * 获取热点文章详情。
   *
   * @param articleId 文章 ID
   * @return 文章视图
   */
  @GetMapping("/hot-news/articles/{articleId}")
  @Operation(summary = "获取热点文章详情")
  public ApiResult<HotNewsArticleView> getHotArticle(@PathVariable String articleId) {
    return ApiResult.ok(hotNews.getHotArticle(articleId));
  }

  /**
   * 更新热点文章。
   *
   * @param articleId 文章 ID
   * @param body      更新请求；可为 null
   * @return 更新后的文章视图
   */
  @PutMapping("/hot-news/articles/{articleId}")
  @Operation(summary = "更新热点文章")
  public ApiResult<HotNewsArticleView> updateHotArticle(
      @PathVariable String articleId, @Valid @RequestBody(required = false) HotNewsArticleUpdateRequest body) {
    return ApiResult.ok(hotNews.updateHotArticle(articleId, body));
  }

  /**
   * 列出热点文章生成运行。
   *
   * @param articleId 文章 ID
   * @return 生成运行 Job 列表
   */
  @GetMapping("/hot-news/articles/{articleId}/generation-runs")
  @Operation(summary = "列出热点文章生成运行")
  public ApiResult<List<JobView>> listHotArticleGenerationRuns(@PathVariable String articleId) {
    return ApiResult.ok(hotNews.listGenerationRuns(articleId));
  }

  /**
   * 更新热点文章生成设置。
   *
   * @param articleId 文章 ID
   * @param body      生成设置请求；可为 null
   * @return 更新后的文章视图
   */
  @PatchMapping("/hot-news/articles/{articleId}/generation-settings")
  @Operation(summary = "更新热点文章生成设置")
  public ApiResult<HotNewsArticleView> patchHotArticleGenerationSettings(
      @PathVariable String articleId,
      @Valid @RequestBody(required = false) HotNewsGenerationSettingsRequest body) {
    return ApiResult.ok(hotNews.updateGenerationSettings(articleId, body));
  }

  /**
   * 列出热点文章版本。
   *
   * @param articleId  文章 ID
   * @param documentId 统一文档 id；可空
   * @return 版本列表
   */
  @GetMapping("/hot-news/articles/{articleId}/versions")
  @Operation(summary = "列出热点文章版本")
  public ApiResult<List<HotNewsArticleVersionView>> listHotArticleVersions(
      @PathVariable String articleId,
      @RequestParam(required = false) String documentId) {
    return ApiResult.ok(hotNews.listArticleVersions(articleId, documentId));
  }

  /**
   * 启动热点文章生成运行。
   *
   * @param articleId 文章 ID
   * @param body      生成运行请求；可为 null
   * @return 已入队的生成 Job
   */
  @PostMapping("/hot-news/articles/{articleId}/generation-runs")
  @Operation(summary = "启动热点文章生成运行")
  public ApiResult<JobView> startHotArticleGenerationRun(
      @PathVariable String articleId,
      @Valid @RequestBody(required = false) HotNewsGenerationRunRequest body) {
    Map<String, Object> input = generationInputEnricher.enrich(body);
    return ApiResult.ok(hotNews.startGenerationRun(articleId, input));
  }

  /**
   * 列出选题下热点文章。
   *
   * @param topicId 选题 ID
   * @return 文章列表
   */
  @GetMapping("/hot-news/topics/{topicId}/articles")
  @Operation(summary = "列出选题下热点文章")
  public ApiResult<List<HotNewsArticleView>> topicArticles(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.listArticlesByTopic(topicId));
  }

}
