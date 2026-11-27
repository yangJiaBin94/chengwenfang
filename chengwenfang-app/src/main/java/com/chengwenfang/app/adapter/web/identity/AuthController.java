package com.chengwenfang.app.adapter.web.identity;

import com.chengwenfang.app.application.identity.MeAvatarService;
import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.identity.facade.AuthFacade;
import com.chengwenfang.identity.facade.AuthProperties;
import com.chengwenfang.identity.facade.dto.request.ChangePasswordRequest;
import com.chengwenfang.identity.facade.dto.request.LoginRequest;
import com.chengwenfang.identity.facade.dto.request.RegisterRequest;
import com.chengwenfang.identity.facade.dto.request.UpdateProfileRequest;
import com.chengwenfang.identity.facade.dto.request.VerifyPasswordRequest;
import com.chengwenfang.identity.facade.dto.view.AuthStatusView;
import com.chengwenfang.identity.facade.dto.view.CurrentUserView;
import com.chengwenfang.identity.facade.dto.view.LoginResultView;
import cn.hutool.extra.servlet.JakartaServletUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证 HTTP 适配：登录 / 登出 / 注册 / 改密 / 当前用户。
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
   * @param body    登录体
   * @param request HTTP 请求（取客户端 IP）
   * @return 令牌与用户
   */
  @PostMapping("/login")
  @Operation(summary = "登录")
  public ApiResult<LoginResultView> login(
      @Valid @RequestBody LoginRequest body, HttpServletRequest request) {
    return ApiResult.ok(authFacade.login(body, JakartaServletUtil.getClientIP(request)));
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
   * @param body    注册体
   * @param request HTTP 请求（取客户端 IP）
   * @return 登录结果
   */
  @PostMapping("/register")
  @Operation(summary = "注册")
  public ApiResult<LoginResultView> register(
      @Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
    return ApiResult.ok(authFacade.register(body, JakartaServletUtil.getClientIP(request)));
  }

  /**
   * 本人修改密码；成功后全部会话失效，需重新登录。
   *
   * @param body 当前密码与新密码
   * @return 空成功
   */
  @PostMapping("/change-password")
  @Operation(summary = "修改密码")
  public ApiResult<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
    authFacade.changePassword(body);
    return ApiResult.ok(null);
  }

  /**
   * 校验当前密码是否正确（改密表单失焦预检）。
   *
   * @param body 当前密码
   * @return 空成功
   */
  @PostMapping("/verify-password")
  @Operation(summary = "校验当前密码")
  public ApiResult<Void> verifyPassword(@Valid @RequestBody VerifyPasswordRequest body) {
    authFacade.verifyCurrentPassword(body);
    return ApiResult.ok(null);
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
 * 当前用户接口（设计约定 {@code /api/me}）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "认证")
class MeController {
  private final AuthFacade authFacade;
  private final MeAvatarService meAvatarService;

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

  /**
   * 更新展示名。
   *
   * @param body 资料体
   * @return 更新后用户
   */
  @PatchMapping("/me")
  @Operation(summary = "更新资料")
  public ApiResult<CurrentUserView> updateProfile(@Valid @RequestBody UpdateProfileRequest body) {
    return ApiResult.ok(authFacade.updateProfile(body));
  }

  /**
   * 上传并绑定头像。
   *
   * @param file 图片文件
   * @return 更新后用户
   */
  @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "上传头像")
  public ApiResult<CurrentUserView> uploadAvatar(@RequestPart("file") MultipartFile file)
      throws IOException {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCodes.AVATAR_UPLOAD_REJECTED, "请选择头像图片", 400);
    }
    return ApiResult.ok(
        meAvatarService.uploadAvatar(file.getOriginalFilename(), file.getContentType(), file.getBytes()));
  }
}
