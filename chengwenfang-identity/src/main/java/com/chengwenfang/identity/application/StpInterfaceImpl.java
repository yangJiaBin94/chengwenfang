package com.chengwenfang.identity.application;

import cn.dev33.satoken.stp.StpInterface;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 角色/权限加载。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {
  private final AuthFacadeImpl authFacade;

  /**
   * 返回账号拥有的权限码集合。
   *
   * @param loginId   账号 id
   * @param loginType 账号类型
   * @return 权限码列表
   */
  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    List<String> roles = authFacade.loadRoleCodes(String.valueOf(loginId));
    return authFacade.loadPermissionCodes(roles);
  }

  /**
   * 返回账号拥有的角色标识集合。
   *
   * @param loginId   账号 id
   * @param loginType 账号类型
   * @return 角色列表
   */
  @Override
  public List<String> getRoleList(Object loginId, String loginType) {
    List<String> roles = authFacade.loadRoleCodes(String.valueOf(loginId));
    return roles == null ? new ArrayList<>() : roles;
  }
}
