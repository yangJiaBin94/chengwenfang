package com.chengwenfang.sourcing.application.videoparse;

import cn.hutool.core.util.StrUtil;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 视频解析归属策略解析器：按 {@code ownerType} 从已注册策略中选取实现。
 * <p>通用路径（如 {@link #markFailed}）经此路由；owner 特有流程仍可直接注入具体策略类。
 */
@Component
public class VideoParseOwnerStrategyResolver {

  /** Spring 注入的全部归属策略。 */
  private final List<VideoParseOwnerStrategy<?>> strategies;

  /**
   * @param strategies 全部 {@link VideoParseOwnerStrategy} 实现
   */
  public VideoParseOwnerStrategyResolver(List<VideoParseOwnerStrategy<?>> strategies) {
    this.strategies = List.copyOf(strategies);
  }

  /**
   * 按归属类型解析策略。
   *
   * @param ownerType {@link com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes} 常量
   * @return 策略；未命中或类型为空时 null
   */
  public VideoParseOwnerStrategy<?> resolve(String ownerType) {
    if (StrUtil.isBlank(ownerType)) {
      return null;
    }
    for (VideoParseOwnerStrategy<?> strategy : strategies) {
      if (strategy.supports(ownerType)) {
        return strategy;
      }
    }
    return null;
  }

  /**
   * 按归属类型解析策略；未命中则抛出。
   *
   * @param ownerType 归属类型
   * @return 策略
   * @throws IllegalArgumentException 无对应策略时
   */
  public VideoParseOwnerStrategy<?> require(String ownerType) {
    VideoParseOwnerStrategy<?> strategy = resolve(ownerType);
    if (strategy == null) {
      throw new IllegalArgumentException("未注册视频解析归属策略: " + ownerType);
    }
    return strategy;
  }

  /**
   * 通用失败标记：按归属类型路由到对应策略。
   *
   * @param ownerType    归属类型
   * @param ownerId      归属主键
   * @param errorMessage 原始错误
   */
  public void markFailed(String ownerType, String ownerId, String errorMessage) {
    require(ownerType).markFailed(ownerId, errorMessage);
  }
}
