package com.chengwenfang.identity.application;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.identity.domain.entity.RolePermissionEntity;
import com.chengwenfang.identity.domain.entity.UserEntity;
import com.chengwenfang.identity.domain.entity.UserRoleEntity;
import com.chengwenfang.identity.facade.AuthFacade;
import com.chengwenfang.identity.facade.AuthProperties;
import com.chengwenfang.identity.facade.dto.request.LoginRequest;
import com.chengwenfang.identity.facade.dto.request.RegisterRequest;
import com.chengwenfang.identity.facade.dto.view.CurrentUserView;
import com.chengwenfang.identity.facade.dto.view.LoginResultView;
import com.chengwenfang.identity.facade.enums.UserRole;
import com.chengwenfang.identity.infrastructure.mapper.RolePermissionMapper;
import com.chengwenfang.identity.infrastructure.mapper.UserMapper;
import com.chengwenfang.identity.infrastructure.mapper.UserRoleMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证门面实现：账号密码 + Sa-Token 会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFacadeImpl implements AuthFacade {
  private final UserMapper userMapper;
  private final UserRoleMapper userRoleMapper;
  private final RolePermissionMapper rolePermissionMapper;
  private final AuthProperties authProperties;

  /**
   * {@inheritDoc}
   */
  @Override
  public LoginResultView login(LoginRequest request) {
    String username = StrUtil.trim(request.getUsername());
    UserEntity user = userMapper.selectOne(
        new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username).last("LIMIT 1"));
    if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, "用户名或密码错误", 401);
    }
    // 校验密码哈希
    if (!BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, "用户名或密码错误", 401);
    }
    StpUtil.login(user.getId());
    log.info("用户登录成功 userId={} username={}", user.getId(), user.getUsername());
    return toLoginResult(user);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logout() {
    if (StpUtil.isLogin()) {
      String loginId = String.valueOf(StpUtil.getLoginId());
      StpUtil.logout();
      log.info("用户已登出 userId={}", loginId);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Transactional
  public LoginResultView register(RegisterRequest request) {
    if (!authProperties.isRegisterEnabled()) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, "未开放自助注册", 403);
    }
    String username = StrUtil.trim(request.getUsername());
    Long exists = userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
    if (exists != null && exists > 0) {
      throw new BusinessException(ErrorCodes.USERNAME_CONFLICT, "用户名已存在", 409);
    }
    LocalDateTime now = LocalDateTime.now();
    UserEntity user = UserEntity.builder()
        .id(IdsUtil.newId())
        .username(username)
        .passwordHash(BCrypt.hashpw(request.getPassword()))
        .displayName(StrUtil.blankToDefault(StrUtil.trim(request.getDisplayName()), username))
        .enabled(true)
        .createdAt(now)
        .updatedAt(now)
        .build();
    userMapper.insert(user);
    // 新用户默认 EDITOR
    userRoleMapper.insert(UserRoleEntity.builder()
        .userId(user.getId())
        .roleCode(UserRole.EDITOR.getValue())
        .build());
    StpUtil.login(user.getId());
    log.info("用户注册并登录 userId={} username={}", user.getId(), username);
    return toLoginResult(user);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CurrentUserView currentUser() {
    if (!StpUtil.isLogin()) {
      return null;
    }
    String userId = String.valueOf(StpUtil.getLoginId());
    UserEntity user = userMapper.selectById(userId);
    if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
      return null;
    }
    return toCurrentUser(user);
  }

  /**
   * 组装登录结果。
   *
   * @param user 用户实体
   * @return 视图
   */
  private LoginResultView toLoginResult(UserEntity user) {
    SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
    return LoginResultView.builder()
        .token(tokenInfo.getTokenValue())
        .tokenName(tokenInfo.getTokenName())
        .user(toCurrentUser(user))
        .build();
  }

  /**
   * 组装当前用户（含角色权限）。
   *
   * @param user 用户实体
   * @return 视图
   */
  private CurrentUserView toCurrentUser(UserEntity user) {
    List<String> roles = loadRoleCodes(user.getId());
    List<String> permissions = loadPermissionCodes(roles);
    return CurrentUserView.builder()
        .id(user.getId())
        .username(user.getUsername())
        .displayName(user.getDisplayName())
        .roles(roles)
        .permissions(permissions)
        .build();
  }

  /**
   * 加载用户角色编码。
   *
   * @param userId 用户 ID
   * @return 角色列表
   */
  List<String> loadRoleCodes(String userId) {
    List<UserRoleEntity> rows = userRoleMapper.selectList(
        new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId));
    if (rows == null || rows.isEmpty()) {
      return new ArrayList<>();
    }
    return rows.stream().map(UserRoleEntity::getRoleCode).distinct().collect(Collectors.toList());
  }

  /**
   * 按角色加载权限编码并去重。
   *
   * @param roleCodes 角色编码
   * @return 权限列表
   */
  List<String> loadPermissionCodes(List<String> roleCodes) {
    if (roleCodes == null || roleCodes.isEmpty()) {
      return new ArrayList<>();
    }
    List<RolePermissionEntity> rows = rolePermissionMapper.selectList(
        new LambdaQueryWrapper<RolePermissionEntity>().in(RolePermissionEntity::getRoleCode, roleCodes));
    if (rows == null || rows.isEmpty()) {
      return new ArrayList<>();
    }
    Set<String> codes = new LinkedHashSet<>();
    for (RolePermissionEntity row : rows) {
      codes.add(row.getPermissionCode());
    }
    return new ArrayList<>(codes);
  }
}
