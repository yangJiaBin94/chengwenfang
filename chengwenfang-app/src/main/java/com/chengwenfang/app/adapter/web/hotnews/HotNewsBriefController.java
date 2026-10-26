package com.chengwenfang.app.adapter.web.hotnews;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsBriefConfirmRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
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
import org.springframework.web.bind.annotation.RestController;

/** 选题简报创建与确认。 */
@Tag(name = "热点简报", description = "选题简报创建与确认")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HotNewsBriefController {
  /** 热点资讯领域门面。 */
  private final HotNewsFacade hotNews;

  /**
   * 确认选题简报。
   *
   * @param briefId 简报 ID
   * @param body    确认请求；note 可空
   * @return 确认后的简报视图
   */
  @PostMapping("/hot-news/briefs/{briefId}/confirm")
  @Operation(summary = "确认选题简报")
  public ApiResult<HotNewsBriefView> confirmBrief(
      @PathVariable String briefId, @Valid @RequestBody(required = false) HotNewsBriefConfirmRequest body) {
    String note = body == null ? null : body.getNote();
    return ApiResult.ok(hotNews.confirmBrief(briefId, note));
  }

  /**
   * 列出选题简报。
   *
   * @param topicId 选题 ID
   * @return 简报列表
   */
  @GetMapping("/hot-news/topics/{topicId}/briefs")
  @Operation(summary = "列出选题简报")
  public ApiResult<List<HotNewsBriefView>> topicBriefs(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.listBriefs(topicId));
  }

  /**
   * 基于简报生成热点文章（兼容旧入口）。
   *
   * @param briefId 简报 ID
   * @return 已入队的生成 Job
   */
  @PostMapping("/hot-news/briefs/{briefId}/generate-article")
  @Operation(summary = "基于简报生成热点文章")
  public ApiResult<JobView> generateArticle(@PathVariable String briefId) {
    return ApiResult.ok(hotNews.generateArticleFromBrief(briefId));
  }

  /**
   * 创建选题简报。
   *
   * @param topicId 选题 ID
   * @return 新建简报视图
   */
  @PostMapping("/hot-news/topics/{topicId}/briefs")
  @Operation(summary = "创建选题简报")
  public ApiResult<HotNewsBriefView> createBrief(@PathVariable String topicId) {
    return ApiResult.ok(hotNews.createBrief(topicId));
  }

}
