package com.chengwenfang.app.adapter.web.hotnews;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.PageQuery;
import com.chengwenfang.mode.hotnews.application.hotnews.HotNewsThemeContextResolver;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsEvidenceView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicPageView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsTopicStatusRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.chengwenfang.job.facade.dto.view.JobView;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 选题列表、状态、骨架与确认。 */
@Tag(name = "热点选题", description = "选题列表、状态、骨架与确认")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HotNewsTopicController {
  /** 热点资讯领域门面。 */
  private final HotNewsFacade hotNews;
  /** 内容主题门面（主题软删守卫）。 */
  private final ContentFacade content;
  /** 主题成文上下文解析。 */
  private final HotNewsThemeContextResolver themeContextResolver;

  /**
   * 按主题列出选题。
   *
   * @param themeId 主题 ID
   * @param status  可选状态过滤
   * @param limit   返回条数上限，默认 50
   * @return 选题列表
   */
  @GetMapping("/themes/{themeId}/hot-news/topics")
  @Operation(summary = "按主题列出选题")
  public ApiResult<List<HotNewsTopicView>> themeTopics(
      @PathVariable String themeId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "50") int limit) {
    content.assertThemeActive(themeId);
    return ApiResult.ok(hotNews.listTopics(themeId, status, limit));
  }

  /**
   * 将主题下英文选题提炼为中文标题与事实速览（无需重新抓取）。
   *
   * @param themeId 主题 ID
   * @return 成功写入速览的条数
   */
  @PostMapping("/themes/{themeId}/hot-news/topics/editorialize")
  @Operation(summary = "提炼中文事实速览")
  public ApiResult<Map<String, Object>> editorializeTopics(@PathVariable String themeId) {
    content.assertThemeEnabled(themeId);
    int translated = hotNews.editorializeTopics(themeId);
    return ApiResult.ok(Map.of("translatedTopics", translated));
  }

  /**
   * 列出选题证据。
   *
   * @param topicId 选题 ID
   * @return 证据列表
   */
  @GetMapping("/hot-news/topics/{topicId}/evidence")
  @Operation(summary = "列出选题证据")
  public ApiResult<List<HotNewsEvidenceView>> topicEvidence(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.listEvidence(topicId));
  }

  /**
   * 读取选题文章骨架。
   *
   * @param topicId 选题 ID
   * @return 骨架视图；无则 data 为 null
   */
  @GetMapping("/hot-news/topics/{topicId}/article-skeleton")
  @Operation(summary = "读取选题文章骨架")
  public ApiResult<HotNewsArticleSkeletonView> getArticleSkeleton(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.getArticleSkeleton(topicId));
  }

  /**
   * 列出热点选题。
   *
   * @param themeId 可选主题 ID
   * @param limit   返回条数上限，默认 50
   * @return 选题列表
   */
  @GetMapping("/hot-news/topics")
  @Operation(summary = "列出热点选题")
  public ApiResult<List<HotNewsTopicView>> topics(
      @RequestParam(required = false) String themeId,
      @RequestParam(defaultValue = "50") int limit) {
    return ApiResult.ok(hotNews.listTopics(themeId, limit));
  }

  /**
   * 确保选题下存在文章壳（不入队生成），供资料池/已选题进入工作区。
   *
   * @param topicId 选题 ID
   * @return 文章视图
   */
  @PostMapping("/hot-news/topics/{topicId}/ensure-article")
  @Operation(summary = "确保选题文章壳存在")
  public ApiResult<HotNewsArticleView> ensureArticleForTopic(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.ensureArticleForTopic(topicId));
  }

  /**
   * 获取热点选题详情。
   *
   * @param topicId 选题 ID
   * @return 选题视图
   */
  @GetMapping("/hot-news/topics/{topicId}")
  @Operation(summary = "获取热点选题详情")
  public ApiResult<HotNewsTopicView> getTopic(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.getTopic(topicId));
  }

  /**
   * 更新热点选题状态。
   *
   * @param topicId 选题 ID
   * @param body    状态请求；null 时 status 为 null
   * @return 更新后的选题视图
   */
  @PostMapping("/hot-news/topics/{topicId}/status")
  @Operation(summary = "更新热点选题状态")
  public ApiResult<HotNewsTopicView> setTopicStatus(
      @PathVariable String topicId, @Valid @RequestBody(required = false) HotNewsTopicStatusRequest body) {
    String status = body == null ? null : body.getStatus();
    return ApiResult.ok(hotNews.setTopicStatus(topicId, status));
  }

  /**
   * 列出选题来源。
   *
   * @param topicId 选题 ID
   * @return 来源列表
   */
  @GetMapping("/hot-news/topics/{topicId}/sources")
  @Operation(summary = "列出选题来源")
  public ApiResult<List<HotNewsTopicSourceView>> topicSources(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.listTopicSources(topicId));
  }

  /**
   * 根据资料池与选题拟定文章骨架。
   *
   * @param topicId 选题 ID
   * @return 拟定后的骨架
   */
  @PostMapping("/hot-news/topics/{topicId}/article-skeleton")
  @Operation(summary = "拟定文章骨架")
  public ApiResult<HotNewsArticleSkeletonView> buildArticleSkeleton(@PathVariable String topicId) {
    HotNewsTopicView topic = hotNews.getTopic(topicId);
    String themeContext = themeContextResolver.resolve(topic == null ? null : topic.getThemeId());
    return ApiResult.ok(hotNews.buildArticleSkeleton(topicId, themeContext));
  }

  /**
   * 按主题分页列出热点雷达选题（筛选、关键字、排序在服务端完成）。
   *
   * @param themeId  主题 ID
   * @param page     页码，从 1 开始
   * @param pageSize 每页条数
   * @param keyword  标题/摘要/来源关键字；可空
   * @param filter   today / all / candidate / confirmed；默认 today
   * @param sortBy   writing / heat；默认 writing
   * @return 分页结果（含雷达池候选数与总数）
   */
  @GetMapping("/themes/{themeId}/hot-news/topics/page")
  @Operation(summary = "按主题分页列出热点雷达选题")
  public ApiResult<HotNewsTopicPageView> themeTopicsPage(
      @PathVariable String themeId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "5") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) String sortBy) {
    content.assertThemeActive(themeId);
    PageQuery query = PageQuery.builder().page(page).pageSize(pageSize).keyword(keyword).build();
    return ApiResult.ok(hotNews.pageTopics(themeId, query, filter, sortBy));
  }

  /**
   * 基于选题生成热点文章（按骨架展开，资料池作可选参考）。
   *
   * @param topicId 选题 ID
   * @return 已入队的生成 Job
   */
  @PostMapping("/hot-news/topics/{topicId}/generate-article")
  @Operation(summary = "基于选题生成热点文章")
  public ApiResult<JobView> generateArticleFromTopic(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.generateArticleFromTopic(topicId));
  }

  /**
   * 列出主题下全部已确认选题（含写作中、已完成），供已选题页。
   *
   * @param themeId 主题 ID
   * @return 已确认选题列表
   */
  @GetMapping("/themes/{themeId}/hot-news/confirmed-topics")
  @Operation(summary = "列出主题下全部已确认选题")
  public ApiResult<List<HotNewsTopicView>> themeConfirmedTopics(@PathVariable String themeId) {
    content.assertThemeActive(themeId);
    return ApiResult.ok(hotNews.listConfirmedTopics(themeId));
  }

}
