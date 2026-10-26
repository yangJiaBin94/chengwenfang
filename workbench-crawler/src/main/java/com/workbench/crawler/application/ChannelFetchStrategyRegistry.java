package com.workbench.crawler.application;

import com.workbench.crawler.domain.ChannelFetchStrategy;
import com.workbench.crawler.facade.enums.ChannelType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 渠道抓取策略注册表：按 {@link ChannelType} 绑定唯一策略实现。
 */
@Slf4j
@Component
public class ChannelFetchStrategyRegistry {
  /** 类型 → 策略。 */
  private final Map<ChannelType, ChannelFetchStrategy> strategies = new EnumMap<>(ChannelType.class);

  /**
   * 收集全部策略 Bean 并注册。
   *
   * @param strategyList Spring 注入的策略实现列表
   */
  public ChannelFetchStrategyRegistry(List<ChannelFetchStrategy> strategyList) {
    for (ChannelFetchStrategy strategy : strategyList) {
      ChannelType type = strategy.supports();
      ChannelFetchStrategy previous = strategies.put(type, strategy);
      if (previous != null) {
        log.warn("渠道抓取策略重复注册，后注册覆盖先注册 type={} first={} second={}",
            type.getValue(), previous.getClass().getSimpleName(), strategy.getClass().getSimpleName());
      } else {
        log.info("已注册渠道抓取策略 type={} impl={}", type.getValue(), strategy.getClass().getSimpleName());
      }
    }
  }

  /**
   * 按类型获取策略。
   *
   * @param type 渠道类型
   * @return 策略；未注册时返回 null
   */
  public ChannelFetchStrategy get(ChannelType type) {
    return type == null ? null : strategies.get(type);
  }
}
