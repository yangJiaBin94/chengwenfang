package com.chengwenfang.identity.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.identity.facade.constant.SeedUserIds;

/**
 * 列表查询按所属用户过滤的通用助手（fail-closed）。
 */
public final class OwnerScopeQueryHelper {

  private OwnerScopeQueryHelper() {}

  /**
   * 非管理员按 owner 列等值过滤；无法解析用户时 {@code 1 = 0}。
   *
   * @param query       MyBatis 查询
   * @param ownerScope  数据范围
   * @param ownerColumn 实体 owner 列
   * @param <T>         实体类型
   */
  public static <T> void applyOwnerEquals(
      LambdaQueryWrapper<T> query, OwnerScope ownerScope, SFunction<T, ?> ownerColumn) {
    if (ownerScope.currentUserIsAdmin()) {
      return;
    }
    String ownerId = ownerScope.currentOwnerUserId().orElse(null);
    if (StrUtil.isBlank(ownerId)) {
      query.apply("1 = 0");
      return;
    }
    query.eq(ownerColumn, ownerId);
  }

  /**
   * 提示词包列表：非管理员可见 {@code owner=自己 OR locked=true}。
   *
   * @param query      查询
   * @param ownerScope 数据范围
   * @param ownerColumn owner 列
   * @param lockedColumn locked 列
   * @param <T> 实体类型
   */
  public static <T> void applyOwnerOrLocked(
      LambdaQueryWrapper<T> query,
      OwnerScope ownerScope,
      SFunction<T, ?> ownerColumn,
      SFunction<T, ?> lockedColumn) {
    if (ownerScope.currentUserIsAdmin()) {
      return;
    }
    String ownerId = ownerScope.currentOwnerUserId().orElse(null);
    if (StrUtil.isBlank(ownerId)) {
      query.apply("1 = 0");
      return;
    }
    query.and(wrapper -> wrapper
        .eq(ownerColumn, ownerId)
        .or()
        .eq(lockedColumn, true));
  }

  /**
   * 工作流定义列表：非管理员可见 {@code owner=自己 OR owner=种子管理员}（平台种子）。
   *
   * @param query       查询
   * @param ownerScope  数据范围
   * @param ownerColumn owner 列
   * @param <T>         实体类型
   */
  public static <T> void applyOwnerOrSeedAdmin(
      LambdaQueryWrapper<T> query, OwnerScope ownerScope, SFunction<T, ?> ownerColumn) {
    if (ownerScope.currentUserIsAdmin()) {
      return;
    }
    String ownerId = ownerScope.currentOwnerUserId().orElse(null);
    if (StrUtil.isBlank(ownerId)) {
      query.apply("1 = 0");
      return;
    }
    query.and(wrapper -> wrapper
        .eq(ownerColumn, ownerId)
        .or()
        .eq(ownerColumn, SeedUserIds.ADMIN));
  }
}