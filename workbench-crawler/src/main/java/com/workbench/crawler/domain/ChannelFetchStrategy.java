package com.workbench.crawler.domain;

import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.enums.ChannelType;

/**
 * 渠道抓取策略端口（策略模式）：一种 {@link ChannelType} 对应一个实现。
 * <p>实现须将上游差异消化为统一的 {@link FetchResult} / {@code FetchedItemView}。
 */
public interface ChannelFetchStrategy {

  /**
   * @return 本策略支持的渠道类型
   */
  ChannelType supports();

  /**
   * 执行抓取并返回统一出站结果。
   *
   * @param context 抓取上下文
   * @return 成功或失败结果；失败时勿抛异常，用 ok=false 表达
   */
  FetchResult fetch(ChannelFetchContext context);
}
