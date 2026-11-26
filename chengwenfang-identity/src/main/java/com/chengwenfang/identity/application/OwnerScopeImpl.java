package com.chengwenfang.identity.application;

import cn.dev33.satoken.exception.NotWebContextException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.identity.facade.OwnerContext;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.identity.facade.constant.SeedUserIds;
import com.chengwenfang.identity.facade.enums.UserRole;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户级数据范围默认实现（鉴权始终开启）。
 */
@Service
@RequiredArgsConstructor
public class OwnerScopeImpl implements OwnerScope {
  private final AuthFacadeImpl authFacade;

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<String> currentOwnerUserId() {
    String contextOwnerId = OwnerContext.get();
    if (StrUtil.isNotBlank(contextOwnerId)) {
      return Optional.of(contextOwnerId);
    }
    // ApplicationRunner / Job 等非 Web 线程无 HttpServletRequest，禁止调 Sa-Token
    try {
      if (StpUtil.isLogin()) {
        return Optional.of(String.valueOf(StpUtil.getLoginId()));
      }
    } catch (NotWebContextException ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String effectiveOwnerUserId() {
    return currentOwnerUserId().orElse(SeedUserIds.ADMIN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean currentUserIsAdmin() {
    Optional<String> ownerId = currentOwnerUserId();
    if (ownerId.isEmpty()) {
      return false;
    }
    List<String> roles = authFacade.loadRoleCodes(ownerId.get());
    return roles.stream().anyMatch(UserRole::isAdmin);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<String> requireOwnerUserIdForWrite() {
    Optional<String> ownerId = currentOwnerUserId();
    if (ownerId.isEmpty()) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, "请先登录", 401);
    }
    return ownerId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void assertCanAccess(String resourceOwnerUserId) {
    // 旧数据未写 owner 时按种子管理员归属
    String normalizedOwnerId = StrUtil.blankToDefault(resourceOwnerUserId, SeedUserIds.ADMIN);
    Optional<String> currentOwnerId = currentOwnerUserId();
    if (currentOwnerId.isEmpty()) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, "请先登录", 401);
    }
    if (currentUserIsAdmin()) {
      return;
    }
    if (!currentOwnerId.get().equals(normalizedOwnerId)) {
      throw new BusinessException(ErrorCodes.OWNER_MISMATCH, "无权访问该资源", 403);
    }
  }
}
