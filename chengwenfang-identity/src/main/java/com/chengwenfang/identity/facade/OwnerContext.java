package com.chengwenfang.identity.facade;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.identity.facade.constant.SeedUserIds;

/**
 * 无 HTTP 会话时的所属用户覆盖（如 Job Worker 按任务快照恢复）。
 */
public final class OwnerContext {
  private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

  private OwnerContext() {}

  /**
   * 当前覆盖的所属用户；无覆盖返回 null。
   *
   * @return 用户 ID 或 null
   */
  public static String get() {
    return HOLDER.get();
  }

  /**
   * 在指定所属用户下执行，结束时清理。
   * <p>空白 {@code ownerUserId} 回退为种子管理员（旧数据/脏快照默认归属）。
   *
   * @param ownerUserId 所属用户；空白则按种子管理员
   * @param action      业务动作
   */
  public static void runAs(String ownerUserId, Runnable action) {
    String effectiveOwnerId = StrUtil.blankToDefault(ownerUserId, SeedUserIds.ADMIN);
    String previous = HOLDER.get();
    HOLDER.set(effectiveOwnerId);
    try {
      action.run();
    } finally {
      if (previous == null) {
        HOLDER.remove();
      } else {
        HOLDER.set(previous);
      }
    }
  }

  /**
   * 捕获当前所属覆盖，供线程池 / {@code CompletableFuture} 传播。
   *
   * @param action 异步动作
   * @return 进入子线程后自动 {@link #runAs} 的包装
   */
  public static Runnable wrap(Runnable action) {
    String ownerId = get();
    return () -> runAs(ownerId, action);
  }

  /**
   * 捕获当前所属覆盖后执行有返回值的异步动作。
   *
   * @param supplier 异步供应
   * @param <T>      结果类型
   * @return 进入子线程后自动 {@link #runAs} 的包装
   */
  public static <T> java.util.function.Supplier<T> wrap(java.util.function.Supplier<T> supplier) {
    String ownerId = get();
    return () -> {
      java.util.concurrent.atomic.AtomicReference<T> box =
          new java.util.concurrent.atomic.AtomicReference<>();
      runAs(ownerId, () -> box.set(supplier.get()));
      return box.get();
    };
  }
}
