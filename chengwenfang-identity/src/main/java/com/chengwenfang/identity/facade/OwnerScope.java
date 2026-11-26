package com.chengwenfang.identity.facade;

/**
 * 用户级数据范围：返回当前用户 ID；普通用户只看见自己的资源。
 * <p>ADMIN 默认仍绑定自身 owner（个人资源）；跨用户运维接口另行显式放行。
 * <p>Job Worker / SSE 通过 {@link OwnerContext} 绑定所属用户。
 */
public interface OwnerScope {
  /**
   * 当前应写入 / 过滤的所属用户 ID。
   * <p>优先 {@link OwnerContext}，否则 Sa-Token 登录用户；都无则 empty。
   *
   * @return 用户 ID
   */
  java.util.Optional<String> currentOwnerUserId();

  /**
   * 有效所属用户：OwnerContext → 登录用户 → 种子管理员（写入兜底）。
   *
   * @return 非空用户 ID
   */
  String effectiveOwnerUserId();

  /**
   * 当前解析用户是否管理员（可绕过部分列表过滤的运维场景）。
   *
   * @return 管理员为 true；无法解析用户为 false
   */
  boolean currentUserIsAdmin();

  /**
   * 要求存在所属用户：已登录或 OwnerContext 时返回 userId，否则抛未登录。
   *
   * @return 写入用 owner
   */
  java.util.Optional<String> requireOwnerUserIdForWrite();

  /**
   * 校验资源归属：{@link OwnerContext} 覆盖时按覆盖用户匹配；
   * 已登录管理员放行；非管理员且 owner 不匹配则抛 OWNER_MISMATCH；
   * 既未登录又无 OwnerContext 时抛 UNAUTHORIZED。
   *
   * @param resourceOwnerUserId 资源上的 owner_user_id
   */
  void assertCanAccess(String resourceOwnerUserId);
}
