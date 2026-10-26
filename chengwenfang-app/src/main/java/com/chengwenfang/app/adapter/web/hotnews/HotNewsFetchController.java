package com.chengwenfang.app.adapter.web.hotnews;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.sourcing.facade.constant.HotNewsFetchKeys;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsFetchRunView;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsFetchStartRequest;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsItemView;
import com.chengwenfang.job.facade.dto.view.JobView;
import jakarta.validation.Valid;
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

/** 雷达抓取、源条目与采集运行。 */
@Tag(name = "热点抓取", description = "雷达抓取、源条目与采集运行")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HotNewsFetchController {
  /** 热点资讯领域门面。 */
  private final HotNewsFacade hotNews;
  /** 内容主题门面（主题软删守卫）。 */
  private final ContentFacade content;
  /** 雷达采集入参补全（工作流 id）。 */
  private final HotNewsFetchInputEnricher fetchInputEnricher;

  /**
   * 启动热点抓取任务。
   *
   * @param body 抓取请求；null 时 themeId=default、hours=72、channelIds 空
   * @return 已入队的抓取 Job
   */
  @PostMapping("/hot-news/fetch")
  @Operation(summary = "启动热点抓取任务")
  public ApiResult<JobView> fetch(@Valid @RequestBody(required = false) HotNewsFetchStartRequest body) {
    // 缺省：default 主题、全部频道、72 小时窗口
    HotNewsFetchStartRequest req = body == null ? new HotNewsFetchStartRequest() : body;
    String themeId = req.getThemeId() == null || req.getThemeId().isBlank()
        ? HotNewsFetchKeys.DEFAULT_THEME_ID
        : req.getThemeId();
    List<String> channelIds = req.getChannelIds() == null ? List.of() : req.getChannelIds();
    int hours = req.getHours() == null ? 72 : req.getHours();
    return ApiResult.ok(hotNews.startFetch(
        themeId, channelIds, hours, fetchInputEnricher.enrich(themeId, channelIds, hours)));
  }

  /**
   * 按主题启动抓取任务。
   *
   * @param themeId 主题 ID
   * @param body    抓取请求；null 时 hours=72、channelIds 空
   * @return 已入队的抓取 Job
   */
  @PostMapping("/themes/{themeId}/hot-news/fetch-runs")
  @Operation(summary = "按主题启动抓取任务")
  public ApiResult<JobView> startThemeFetch(
      @PathVariable String themeId, @Valid @RequestBody(required = false) HotNewsFetchStartRequest body) {
    content.assertThemeEnabled(themeId);
    HotNewsFetchStartRequest req = body == null ? new HotNewsFetchStartRequest() : body;
    List<String> channelIds = req.getChannelIds() == null ? List.of() : req.getChannelIds();
    int hours = req.getHours() == null ? 72 : req.getHours();
    return ApiResult.ok(hotNews.startFetch(
        themeId, channelIds, hours, fetchInputEnricher.enrich(themeId, channelIds, hours)));
  }

  /**
   * 列出主题下雷达采集 Job（执行记录页左侧历史）。
   *
   * @param themeId 主题 ID
   * @return Job 列表
   */
  @GetMapping("/themes/{themeId}/hot-news/collect-runs")
  @Operation(summary = "列出主题雷达采集执行记录")
  public ApiResult<List<JobView>> listCollectRuns(@PathVariable String themeId) {
    content.assertThemeEnabled(themeId);
    return ApiResult.ok(hotNews.listRadarCollectRuns(themeId));
  }

  /**
   * 列出热点源条目。
   *
   * @param themeId 可选主题 ID
   * @param limit   返回条数上限，默认 50
   * @return 源条目列表
   */
  @GetMapping("/hot-news/items")
  @Operation(summary = "列出热点源条目")
  public ApiResult<List<HotNewsItemView>> items(
      @RequestParam(required = false) String themeId,
      @RequestParam(defaultValue = "50") int limit) {
    return ApiResult.ok(hotNews.listItems(themeId, limit));
  }

  /**
   * 按主题列出抓取运行。
   *
   * @param themeId 主题 ID
   * @return 抓取运行列表
   */
  @GetMapping("/themes/{themeId}/hot-news/fetch-runs")
  @Operation(summary = "按主题列出抓取运行")
  public ApiResult<List<HotNewsFetchRunView>> themeFetchRuns(@PathVariable String themeId) {
    content.assertThemeActive(themeId);
    return ApiResult.ok(hotNews.listFetchRuns(themeId));
  }

  /**
   * 为主题下待解析雷达信源视频入队解析任务。
   *
   * @param themeId 主题 ID
   * @return 已入队 Job；无可解析条目时 data 为 null
   */
  @PostMapping("/themes/{themeId}/hot-news/source-items/video-parse")
  @Operation(summary = "启动信源视频解析")
  public ApiResult<JobView> startSourceVideoParse(@PathVariable String themeId) {
    content.assertThemeEnabled(themeId);
    return ApiResult.ok(hotNews.enqueuePendingSourceVideoParse(themeId));
  }

}
