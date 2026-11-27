package com.chengwenfang.app.adapter.web.identity;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.PageQuery;
import com.chengwenfang.common.PageResult;
import com.chengwenfang.identity.facade.UserFacade;
import com.chengwenfang.identity.facade.dto.request.CreateUserRequest;
import com.chengwenfang.identity.facade.dto.request.SetUserEnabledRequest;
import com.chengwenfang.identity.facade.dto.view.ResetPasswordResultView;
import com.chengwenfang.identity.facade.dto.view.UserListItemView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理 HTTP 适配（仅管理员）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理")
public class UserController {
  private final UserFacade userFacade;

  /**
   * 分页列出用户。
   *
   * @param page     页码，从 1 开始
   * @param pageSize 每页条数
   * @return 分页结果
   */
  @GetMapping
  @Operation(summary = "用户分页列表")
  public ApiResult<PageResult<UserListItemView>> pageUsers(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize) {
    PageQuery query = PageQuery.builder().page(page).pageSize(pageSize).build();
    return ApiResult.ok(userFacade.pageUsers(query));
  }

  /**
   * 创建用户。
   *
   * @param body 创建体
   * @return 新建用户
   */
  @PostMapping
  @Operation(summary = "创建用户")
  public ApiResult<UserListItemView> createUser(@Valid @RequestBody CreateUserRequest body) {
    return ApiResult.ok(userFacade.createUser(body));
  }

  /**
   * 启停用户。
   *
   * @param userId 用户 ID
   * @param body   启停体
   * @return 更新后用户
   */
  @PatchMapping("/{userId}/enabled")
  @Operation(summary = "启停用户")
  public ApiResult<UserListItemView> setUserEnabled(
      @PathVariable String userId, @Valid @RequestBody SetUserEnabledRequest body) {
    return ApiResult.ok(userFacade.setUserEnabled(userId, body));
  }

  /**
   * 重置用户密码，明文仅返回一次。
   *
   * @param userId 用户 ID
   * @return 重置结果
   */
  @PostMapping("/{userId}/reset-password")
  @Operation(summary = "重置用户密码")
  public ApiResult<ResetPasswordResultView> resetPassword(@PathVariable String userId) {
    return ApiResult.ok(userFacade.resetPassword(userId));
  }
}
