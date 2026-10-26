package com.workbench.app.adapter.web.notify;

import com.workbench.common.ApiResult;
import com.workbench.notification.facade.NotificationFacade;
import com.workbench.notification.facade.dto.view.NotificationListView;
import com.workbench.notification.facade.dto.view.NotificationOkView;
import com.workbench.notification.facade.dto.request.NotificationReadRequest;
import com.workbench.notification.facade.dto.view.NotificationSummaryView;
import com.workbench.notification.facade.dto.view.NotificationView;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知相关 HTTP 接口。
 * <p>薄适配层：参数归一后委托 {@link NotificationFacade}。
 */
@Tag(name = "通知", description = "通知列表、汇总与已读状态")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {
  /** 通知领域门面。 */
  private final NotificationFacade notifications;

  /**
   * 按状态列出通知。
   *
   * @param status 可选过滤状态；空或 all 表示全部
   * @param limit  可选条数上限
   * @return 统一包装的通知列表视图
   */
  @GetMapping("/notifications")
  @Operation(summary = "按状态列出通知")
  public ApiResult<NotificationListView> notifications(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer limit) {
    // 直接透传查询参数给门面
    return ApiResult.ok(notifications.list(status, limit));
  }

  /**
   * 通知汇总（未读数、最新创建时间）。
   *
   * @return 统一包装的汇总视图
   */
  @GetMapping("/notifications/summary")
  @Operation(summary = "通知汇总")
  public ApiResult<NotificationSummaryView> notificationSummary() {
    return ApiResult.ok(notifications.summary());
  }

  /**
   * 更新单条通知已读状态。
   *
   * @param id   通知主键
   * @param body 可选；{@code read} 缺省为 true
   * @return 更新后的通知视图
   */
  @PatchMapping("/notifications/{id}")
  @Operation(summary = "更新通知已读状态")
  public ApiResult<NotificationView> patchNotification(
      @PathVariable String id, @Valid @RequestBody(required = false) NotificationReadRequest body) {
    // body 为空或 read 未传时按已读处理
    boolean read = body == null || body.getRead() == null || Boolean.TRUE.equals(body.getRead());
    return ApiResult.ok(notifications.markRead(id, read));
  }

  /**
   * 全部通知标为已读。
   *
   * @return 标读后的汇总视图
   */
  @PostMapping("/notifications/read-all")
  @Operation(summary = "全部通知标为已读")
  public ApiResult<NotificationSummaryView> readAllNotifications() {
    return ApiResult.ok(notifications.readAll());
  }

  /**
   * 将指定通知标为已读（仅返回 ok）。
   *
   * @param id 通知主键
   * @return {@code ok=true} 的确认视图
   */
  @PostMapping("/notifications/{id}/read")
  @Operation(summary = "将指定通知标为已读")
  public ApiResult<NotificationOkView> read(@PathVariable String id) {
    // 副作用在门面；响应只回执成功
    notifications.markRead(id);
    return ApiResult.ok(NotificationOkView.builder().ok(true).build());
  }
}
