package com.workbench.sourcing.application.videoparse;

/**
 * 视频解析按归属类型的泛型策略端口。
 * <p>资料池条目与雷达信源在目标加载、成功回写、失败标记、弃用/挂接上行为不同；
 * 由 {@link com.workbench.sourcing.application.HotNewsVideoParseService} 按 Job 场景选择策略。
 * 类型参数 {@code T} 为归属实体（如资料池条目、信源）。
 *
 * @param <T> 归属实体类型
 */
public interface VideoParseOwnerStrategy<T> {

  /**
   * @return {@link com.workbench.sourcing.facade.constant.VideoParseOwnerTypes} 常量
   */
  String ownerType();

  /**
   * @param ownerType 归属类型
   * @return 是否由本策略处理
   */
  boolean supports(String ownerType);

  /**
   * 将单条标记为解析失败，避免异常中断后状态停在 running。
   *
   * @param ownerId      归属主键
   * @param errorMessage 原始错误
   */
  void markFailed(String ownerId, String errorMessage);

  /**
   * 按主键重载并水合解析状态；不存在返回 null。
   *
   * @param ownerId 归属主键
   * @return 实体；不存在时 null
   */
  T reload(String ownerId);
}
