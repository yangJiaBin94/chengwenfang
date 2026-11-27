package com.chengwenfang.identity.facade;

import com.chengwenfang.identity.facade.dto.request.ChangePasswordRequest;
import com.chengwenfang.identity.facade.dto.request.LoginRequest;
import com.chengwenfang.identity.facade.dto.request.RegisterRequest;
import com.chengwenfang.identity.facade.dto.request.UpdateProfileRequest;
import com.chengwenfang.identity.facade.dto.request.VerifyPasswordRequest;
import com.chengwenfang.identity.facade.dto.view.CurrentUserView;
import com.chengwenfang.identity.facade.dto.view.LoginResultView;

/**
 * 认证门面：登录、登出、注册、改密、资料与当前用户。
 */
public interface AuthFacade {
  /**
   * 账号密码登录。
   *
   * @param request  登录入参
   * @param clientIp 客户端 IP，可空
   * @return 令牌与用户摘要
   */
  LoginResultView login(LoginRequest request, String clientIp);

  /** 登出当前会话。 */
  void logout();

  /**
   * 自助注册（受 {@code chengwenfang.auth.register-enabled} 控制）。
   *
   * @param request  注册入参
   * @param clientIp 客户端 IP，可空
   * @return 登录结果（注册后自动登录）
   */
  LoginResultView register(RegisterRequest request, String clientIp);

  /**
   * 当前登录用户修改密码；成功后踢掉该用户全部会话。
   *
   * @param request 当前密码与新密码
   */
  void changePassword(ChangePasswordRequest request);

  /**
   * 校验当前登录用户的密码是否正确；错误时抛业务异常。
   *
   * @param request 当前密码
   */
  void verifyCurrentPassword(VerifyPasswordRequest request);

  /**
   * 当前登录用户；未登录或鉴权关闭时返回 null。
   *
   * @return 用户视图或 null
   */
  CurrentUserView currentUser();

  /**
   * 当前用户更新展示名。
   *
   * @param request 资料入参
   * @return 更新后的用户视图
   */
  CurrentUserView updateProfile(UpdateProfileRequest request);

  /**
   * 当前用户绑定新头像文件，返回被替换的旧文件 ID。
   *
   * @param avatarFileId stored_files 主键
   * @return 旧头像文件 ID；原先无头像时为 null
   */
  String replaceAvatarFile(String avatarFileId);
}
