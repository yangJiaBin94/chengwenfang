package com.chengwenfang.app.adapter.web.identity;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.identity.facade.AuthProperties;
import com.chengwenfang.identity.facade.AuthFacade;
import com.chengwenfang.identity.facade.dto.request.LoginRequest;
import com.chengwenfang.identity.facade.dto.request.RegisterRequest;
import com.chengwenfang.identity.facade.dto.view.AuthStatusView;
import com.chengwenfang.identity.facade.dto.view.CurrentUserView;
import com.chengwenfang.identity.facade.dto.view.LoginResultView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 HTTP 适配：登录 / 登出 / 注册 / 当前用户。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证")
public class AuthController {
  private final AuthFacade authFacade;
  private final AuthProperties authProperties;

  /**
   * 账号密码登录。
   *
   * @param body 登录体
   * @return 令牌与用户
   */
  @PostMapping("/login")
  @Operation(summary = "登录")
  public ApiResult<LoginResultView> login(@Valid @RequestBody LoginRequest body) {
    return ApiResult.ok(authFacade.login(body));
  }

  /**
   * 登出。
   *
   * @return 空成功
   */
  @PostMapping("/logout")
  @Operation(summary = "登出")
  public ApiResult<Void> logout() {
    authFacade.logout();
    return ApiResult.ok(null);
  }

  /**
   * 自助注册（需开启 register-enabled）。
   *
   * @param body 注册体
   * @return 登录结果
   */
  @PostMapping("/register")
  @Operation(summary = "注册")
  public ApiResult<LoginResultView> register(@Valid @RequestBody RegisterRequest body) {
    return ApiResult.ok(authFacade.register(body));
  }

  /**
   * 鉴权公开状态（无需登录，供前端决定是否展示注册入口）。
   *
   * @return registerEnabled
   */
  @GetMapping("/status")
  @Operation(summary = "鉴权公开状态")
  public ApiResult<AuthStatusView> status() {
    return ApiResult.ok(
        AuthStatusView.builder()
            .registerEnabled(authProperties.isRegisterEnabled())
            .build());
  }
}

/**
 * 当前用户接口（设计约定 {@code GET /api/me}）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "认证")
class MeController {
  private final AuthFacade authFacade;

  /**
   * 当前登录用户。
   *
   * @return 用户视图
   */
  @GetMapping("/me")
  @Operation(summary = "当前用户")
  public ApiResult<CurrentUserView> me() {
    CurrentUserView user = authFacade.currentUser();
    if (user == null) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, "请先登录", 401);
    }
    return ApiResult.ok(user);
  }
}
