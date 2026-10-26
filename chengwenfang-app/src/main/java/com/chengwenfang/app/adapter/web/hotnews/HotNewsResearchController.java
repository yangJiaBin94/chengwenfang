package com.chengwenfang.app.adapter.web.hotnews;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.PageQuery;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.request.ManualResearchItemRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.VideoParseRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchConflictView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemPageView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchRunView;
import com.chengwenfang.job.facade.dto.view.JobView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 调研运行、资料池与视频解析。 */
@Tag(name = "热点调研", description = "调研运行、资料池与视频解析")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HotNewsResearchController {
  /** 热点资讯领域门面。 */
  private final HotNewsFacade hotNews;

  /**
   * 启动单条资料视频解析（可指定从某步骤重试）。
   *
   * @param topicId 选题 ID
   * @param itemId  资料条目 ID
   * @param body    可选 fromStep
   * @return 解析 Job
   */
  @PostMapping("/hot-news/topics/{topicId}/research-items/{itemId}/video-parse")
  @Operation(summary = "启动单条视频解析")
  public ApiResult<JobView> startVideoParseItem(
      @PathVariable String topicId,
      @PathVariable String itemId,
      @RequestBody(required = false) VideoParseRequest body) {
    VideoParseRequest request = body == null ? new VideoParseRequest() : body;
    request.setItemIds(List.of(itemId));
    return ApiResult.ok(hotNews.startVideoParse(topicId, request));
  }

  /**
   * 启动资料池视频解析（批量或指定条目）。
   *
   * @param topicId 选题 ID
   * @param body    可选 itemIds / fromStep
   * @return 解析 Job
   */
  @PostMapping("/hot-news/topics/{topicId}/research-items/video-parse")
  @Operation(summary = "启动资料池视频解析")
  public ApiResult<JobView> startVideoParse(
      @PathVariable String topicId, @RequestBody(required = false) VideoParseRequest body) {
    return ApiResult.ok(hotNews.startVideoParse(topicId, body));
  }

  /**
   * 启动选题调研任务。
   *
   * @param topicId 选题 ID
   * @return 已入队的调研 Job
   */
  @PostMapping("/hot-news/topics/{topicId}/research-runs")
  @Operation(summary = "启动选题调研任务")
  public ApiResult<JobView> startTopicResearch(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.startTopicResearch(topicId));
  }

  /**
   * 列出选题调研运行。
   *
   * @param topicId 选题 ID
   * @return 调研运行列表
   */
  @GetMapping("/hot-news/topics/{topicId}/research-runs")
  @Operation(summary = "列出选题调研运行")
  public ApiResult<List<HotNewsResearchRunView>> researchRuns(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.listResearchRuns(topicId));
  }

  /**
   * 手动添加资料：访问 URL 采集标题/摘要，并标注站点与内容形态。
   *
   * @param topicId 选题 ID
   * @param body    含 url
   * @return 新条目
   */
  @PostMapping("/hot-news/topics/{topicId}/research-items")
  @Operation(summary = "手动添加调研资料")
  public ApiResult<HotNewsResearchItemView> addManualResearchItem(
      @PathVariable String topicId, @RequestBody ManualResearchItemRequest body) {
    return ApiResult.ok(hotNews.addManualResearchItem(topicId, body));
  }

  /**
   * 分页列出调研条目（关键字、文章/视频可筛选；数据库 LIMIT/OFFSET）。
   *
   * @param topicId  选题 ID
   * @param page     页码，从 1 开始
   * @param pageSize 每页条数
   * @param keyword  标题/摘要/链接关键字；可空
   * @param media    内容形态：all / article / video；可空视为 all
   * @return 分页结果（含选题级文章/视频统计）
   */
  @GetMapping("/hot-news/topics/{topicId}/research-items/page")
  @Operation(summary = "分页列出调研条目")
  public ApiResult<HotNewsResearchItemPageView> researchItemsPage(
      @PathVariable String topicId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "5") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String media) {
    PageQuery query = PageQuery.builder().page(page).pageSize(pageSize).keyword(keyword).build();
    return ApiResult.ok(hotNews.pageResearchItems(topicId, query, media));
  }

  /**
   * 列出调研冲突。
   *
   * @param topicId 选题 ID
   * @return 冲突列表
   */
  @GetMapping("/hot-news/topics/{topicId}/research-conflicts")
  @Operation(summary = "列出调研冲突")
  public ApiResult<List<HotNewsResearchConflictView>> researchConflicts(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.listResearchConflicts(topicId));
  }

  /**
   * 列出调研条目。
   *
   * @param topicId 选题 ID
   * @return 调研条目列表
   */
  @GetMapping("/hot-news/topics/{topicId}/research-items")
  @Operation(summary = "列出调研条目")
  public ApiResult<List<HotNewsResearchItemView>> researchItems(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.listResearchItems(topicId));
  }

}
