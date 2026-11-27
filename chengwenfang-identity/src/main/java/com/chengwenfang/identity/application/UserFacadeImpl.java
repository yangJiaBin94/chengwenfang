package com.chengwenfang.identity.application;

import cn.dev33.satoken.exception.NotWebContextException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.common.PageQuery;
import com.chengwenfang.common.PageResult;
import com.chengwenfang.identity.domain.entity.UserEntity;
import com.chengwenfang.identity.domain.entity.UserRoleEntity;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.identity.facade.UserFacade;
import com.chengwenfang.identity.facade.constant.AuthPermissions;
import com.chengwenfang.identity.facade.dto.request.CreateUserRequest;
import com.chengwenfang.identity.facade.dto.request.SetUserEnabledRequest;
import com.chengwenfang.identity.facade.dto.view.ResetPasswordResultView;
import com.chengwenfang.identity.facade.dto.view.UserListItemView;
import com.chengwenfang.identity.facade.enums.UserRole;
import com.chengwenfang.identity.infrastructure.mapper.UserMapper;
import com.chengwenfang.identity.infrastructure.mapper.UserRoleMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理门面实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {
  private final UserMapper userMapper;
  private final UserRoleMapper userRoleMapper;
  private final OwnerScope ownerScope;

  /**
   * {@inheritDoc}
   */
  @Override
  public PageResult<UserListItemView> pageUsers(PageQuery query) {
    requireUserManage();
    PageQuery normalized = query == null ? new PageQuery().normalized() : query.normalized();
    Page<UserEntity> mpPage = new Page<>(normalized.getPage(), normalized.getPageSize());
    LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
        .orderByAsc(UserEntity::getCreatedAt);
    // 按昵称（display_name）模糊搜索
    String keyword = StrUtil.trim(normalized.getKeyword());
    if (StrUtil.isNotBlank(keyword)) {
      wrapper.like(UserEntity::getDisplayName, keyword);
    }
    Page<UserEntity> fetched = userMapper.selectPage(mpPage, wrapper);
    List<UserEntity> records = fetched.getRecords();
    List<UserListItemView> items = new ArrayList<>();
    if (records != null) {
      for (UserEntity user : records) {
        items.add(toListItem(user));
      }
    }
    return PageResult.of(normalized, items, fetched.getTotal());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Transactional
  public UserListItemView createUser(CreateUserRequest request) {
    requireUserManage();
    String username = StrUtil.trim(request.getUsername());
    UserRole role = UserRole.from(request.getRole())
        .orElseThrow(() -> new BusinessException(ErrorCodes.INVALID_ROLE, "角色无效", 400));
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
    userRoleMapper.insert(UserRoleEntity.builder()
        .userId(user.getId())
        .roleCode(role.getValue())
        .build());
    log.info("管理员创建用户 userId={} username={} role={}", user.getId(), username, role.getValue());
    return toListItem(user);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Transactional
  public UserListItemView setUserEnabled(String userId, SetUserEnabledRequest request) {
    requireUserManage();
    boolean enabled = Boolean.TRUE.equals(request.getEnabled());
    UserEntity user = requireUser(userId);
    // 禁止停用/启用自己，避免把自己锁死在管理页
    assertNotSelf(user.getId());
    user.setEnabled(enabled);
    user.setUpdatedAt(LocalDateTime.now());
    userMapper.updateById(user);
    if (!enabled) {
      // 停用后踢掉目标用户全部会话
      StpUtil.logout(user.getId());
    }
    log.info("管理员设置用户启用状态 userId={} enabled={}", user.getId(), enabled);
    return toListItem(user);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Transactional
  public ResetPasswordResultView resetPassword(String userId) {
    requireUserManage();
    UserEntity user = requireUser(userId);
    String plainPassword = RandomUtil.randomString(12);
    user.setPasswordHash(BCrypt.hashpw(plainPassword));
    user.setUpdatedAt(LocalDateTime.now());
    userMapper.updateById(user);
    // 密码已变，踢掉全部会话
    StpUtil.logout(user.getId());
    log.info("管理员重置用户密码 userId={} username={}", user.getId(), user.getUsername());
    return ResetPasswordResultView.builder()
        .userId(user.getId())
        .username(user.getUsername())
        .password(plainPassword)
        .build();
  }

  /**
   * 按 ID 加载用户，不存在则抛错。
   *
   * @param userId 用户 ID
   * @return 实体
   */
  private UserEntity requireUser(String userId) {
    if (StrUtil.isBlank(userId)) {
      throw new BusinessException(ErrorCodes.USER_NOT_FOUND, "用户不存在", 404);
    }
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      throw new BusinessException(ErrorCodes.USER_NOT_FOUND, "用户不存在", 404);
    }
    return user;
  }

  /**
   * 禁止对当前登录账号做管理操作。
   *
   * @param targetUserId 目标用户 ID
   */
  private void assertNotSelf(String targetUserId) {
    try {
      if (StpUtil.isLogin() && StrUtil.equals(String.valueOf(StpUtil.getLoginId()), targetUserId)) {
        throw new BusinessException(ErrorCodes.USER_SELF_ACTION_FORBIDDEN, "不能操作当前登录账号", 400);
      }
    } catch (NotWebContextException ignored) {
      // Job 等非 Web 上下文无登录态，跳过自操作校验
    }
  }

  /**
   * 应用层鉴权：与 HTTP 一致，仅认 {@link AuthPermissions#USER_MANAGE}；
   * 非 Web 上下文无 Token 权限时再按管理员角色兜底。
   */
  private void requireUserManage() {
    try {
      if (StpUtil.hasPermission(AuthPermissions.USER_MANAGE)) {
        return;
      }
      // Web 已登录但无权限：不再用 ADMIN 角色放行，避免与网关不一致
      throw new BusinessException(ErrorCodes.FORBIDDEN, "无用户管理权限", 403);
    } catch (NotWebContextException ignored) {
      if (ownerScope.currentUserIsAdmin()) {
        return;
      }
      throw new BusinessException(ErrorCodes.FORBIDDEN, "无用户管理权限", 403);
    }
  }

  /**
   * 组装列表项。
   *
   * @param user 用户实体
   * @return 视图
   */
  private UserListItemView toListItem(UserEntity user) {
    List<UserRoleEntity> roleRows = userRoleMapper.selectList(
        new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, user.getId()));
    List<String> roles = roleRows == null
        ? new ArrayList<>()
        : roleRows.stream().map(UserRoleEntity::getRoleCode).distinct().collect(Collectors.toList());
    return UserListItemView.builder()
        .id(user.getId())
        .username(user.getUsername())
        .displayName(user.getDisplayName())
        .avatarUrl(AvatarUrls.fromFileId(user.getAvatarFileId()))
        .enabled(user.getEnabled())
        .roles(roles)
        .lastLoginAt(user.getLastLoginAt())
        .lastLoginIp(user.getLastLoginIp())
        .lastLoginRegion(user.getLastLoginRegion())
        .build();
  }
}
