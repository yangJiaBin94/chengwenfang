package com.chengwenfang.identity.facade;

import com.chengwenfang.common.PageQuery;
import com.chengwenfang.common.PageResult;
import com.chengwenfang.identity.facade.dto.request.CreateUserRequest;
import com.chengwenfang.identity.facade.dto.request.SetUserEnabledRequest;
import com.chengwenfang.identity.facade.dto.view.ResetPasswordResultView;
import com.chengwenfang.identity.facade.dto.view.UserListItemView;

/**
 * 用户管理门面：分页列表、创建、启停与重置密码（仅管理员调用）。
 */
public interface UserFacade {
  /**
   * 分页列出用户。
   *
   * @param query 分页参数
   * @return 分页结果
   */
  PageResult<UserListItemView> pageUsers(PageQuery query);

  /**
   * 管理员创建用户（不自动登录）。
   *
   * @param request 创建入参
   * @return 新建用户摘要
   */
  UserListItemView createUser(CreateUserRequest request);

  /**
   * 管理员启停用户；禁止操作当前登录账号。
   *
   * @param userId  目标用户 ID
   * @param request 启停入参
   * @return 更新后的列表项
   */
  UserListItemView setUserEnabled(String userId, SetUserEnabledRequest request);

  /**
   * 管理员重置密码：生成随机明文、踢掉目标会话，明文仅返回一次。
   *
   * @param userId 目标用户 ID
   * @return 重置结果
   */
  ResetPasswordResultView resetPassword(String userId);
}
