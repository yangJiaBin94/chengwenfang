package com.workbench.app.adapter.web.hotnews;

import com.workbench.common.ApiResult;
import com.workbench.common.PageQuery;
import com.workbench.content.facade.ContentFacade;
import com.workbench.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelDeleteView;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelPageView;
import com.workbench.sourcing.facade.dto.request.HotNewsChannelPatchRequest;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelTestView;
import com.workbench.sourcing.facade.dto.request.HotNewsChannelUpsertRequest;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelView;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
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

/** 热点信息源频道 CRUD 与测试。 */
@Tag(name = "热点频道", description = "热点信息源频道 CRUD 与测试")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HotNewsChannelController {
  /** 热点资讯领域门面。 */
  private final HotNewsFacade hotNews;
  /** 内容主题门面（主题软删守卫）。 */
  private final ContentFacade content;

  /**
   * 按主题分页列出热点频道（名称/地址关键字、类型、启用状态可筛选）。
   *
   * @param themeId  主题 ID
   * @param page     页码，从 1 开始
   * @param pageSize 每页条数
   * @param keyword  名称或地址关键字；可空
   * @param type     频道类型；可空
   * @param enabled  启用状态；可空
   * @return 分页结果（含主题级启用/停用统计）
   */
  @GetMapping("/themes/{themeId}/hot-news/channels/page")
  @Operation(summary = "按主题分页列出热点频道")
  public ApiResult<HotNewsChannelPageView> themeChannelsPage(
      @PathVariable String themeId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "5") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Boolean enabled) {
    content.assertThemeActive(themeId);
    PageQuery query = PageQuery.builder().page(page).pageSize(pageSize).keyword(keyword).build();
    return ApiResult.ok(hotNews.pageChannels(themeId, query, type, enabled));
  }

  /**
   * 幂等写入热点频道。
   *
   * @param body 写入请求；可为 null
   * @return 写入后的频道视图
   */
  @PutMapping("/hot-news/channels")
  @Operation(summary = "幂等写入热点频道")
  public ApiResult<HotNewsChannelView> upsertChannel(@Valid @RequestBody(required = false) HotNewsChannelUpsertRequest body) {
    return ApiResult.ok(hotNews.upsertChannel(body));
  }

  /**
   * 测试热点频道连通性。
   *
   * @param channelId 频道 ID
   * @return 连通性测试结果
   */
  @PostMapping("/hot-news/channels/{channelId}/test")
  @Operation(summary = "测试热点频道连通性")
  public ApiResult<HotNewsChannelTestView> testChannel(@PathVariable String channelId) {
    return ApiResult.ok(hotNews.testChannel(channelId));
  }

  /**
   * 删除热点频道。
   *
   * @param channelId 频道 ID
   * @return 删除确认视图
   */
  @DeleteMapping("/hot-news/channels/{channelId}")
  @Operation(summary = "删除热点频道")
  public ApiResult<HotNewsChannelDeleteView> deleteChannel(@PathVariable String channelId) {
    return ApiResult.ok(hotNews.deleteChannel(channelId));
  }

  /**
   * 更新热点频道。
   *
   * @param channelId 频道 ID
   * @param body      补丁请求；可为 null
   * @return 更新后的频道视图
   */
  @PatchMapping("/hot-news/channels/{channelId}")
  @Operation(summary = "更新热点频道")
  public ApiResult<HotNewsChannelView> patchChannel(
      @PathVariable String channelId, @Valid @RequestBody(required = false) HotNewsChannelPatchRequest body) {
    return ApiResult.ok(hotNews.patchChannel(channelId, body));
  }

  /**
   * 在主题下创建热点频道。
   *
   * @param themeId 主题 ID
   * @param body    创建请求；可为 null
   * @return 新建频道视图
   */
  @PostMapping("/themes/{themeId}/hot-news/channels")
  @Operation(summary = "在主题下创建热点频道")
  public ApiResult<HotNewsChannelView> createThemeChannel(
      @PathVariable String themeId, @Valid @RequestBody(required = false) HotNewsChannelCreateRequest body) {
    content.assertThemeEnabled(themeId);
    return ApiResult.ok(hotNews.createChannel(themeId, body));
  }

  /**
   * 列出热点信息源频道。
   *
   * @param themeId 可选主题 ID；空则不过滤
   * @return 频道视图列表
   */
  @GetMapping("/hot-news/channels")
  @Operation(summary = "列出热点信息源频道")
  public ApiResult<List<HotNewsChannelView>> channels(@RequestParam(required = false) String themeId) {
    return ApiResult.ok(hotNews.listChannels(themeId));
  }

  /**
   * 按主题列出热点频道。
   *
   * @param themeId 主题 ID
   * @return 频道列表
   */
  @GetMapping("/themes/{themeId}/hot-news/channels")
  @Operation(summary = "按主题列出热点频道")
  public ApiResult<List<HotNewsChannelView>> themeChannels(@PathVariable String themeId) {
    content.assertThemeActive(themeId);
    return ApiResult.ok(hotNews.listChannels(themeId));
  }

}
