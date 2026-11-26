package com.chengwenfang.identity.facade;

import com.chengwenfang.identity.facade.dto.request.LoginRequest;
import com.chengwenfang.identity.facade.dto.request.RegisterRequest;
import com.chengwenfang.identity.facade.dto.view.CurrentUserView;
import com.chengwenfang.identity.facade.dto.view.LoginResultView;

/**
 * 认证门面：登录、登出、注册与当前用户。
 */
public interface AuthFacade {
  /**
   * 账号密码登录。
   *
   * @param request 登录入参
   * @return 令牌与用户摘要
   */
  LoginResultView login(LoginRequest request);

  /** 登出当前会话。 */
  void logout();

  /**
   * 自助注册（受 {@code chengwenfang.auth.register-enabled} 控制）。
   *
   * @param request 注册入参
   * @return 登录结果（注册后自动登录）
   */
  LoginResultView register(RegisterRequest request);

  /**
   * 当前登录用户；未登录或鉴权关闭时返回 null。
   *
   * @return 用户视图或 null
   */
  CurrentUserView currentUser();
}
