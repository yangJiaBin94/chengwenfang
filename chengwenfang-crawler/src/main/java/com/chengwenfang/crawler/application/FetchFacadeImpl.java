package com.chengwenfang.crawler.application;

import com.chengwenfang.crawler.domain.ChannelFetchContext;
import com.chengwenfang.crawler.domain.ChannelFetchStrategy;
import com.chengwenfang.crawler.facade.FetchFacade;
import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.constant.FetchErrorCodes;
import com.chengwenfang.crawler.facade.dto.request.FetchRequest;
import com.chengwenfang.crawler.facade.dto.view.FetchResult;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * {@link FetchFacade} 实现：按 {@link ChannelType} 路由到策略，统一返回 {@link FetchResult}。
 */
@Slf4j
@Service
public class FetchFacadeImpl implements FetchFacade {
  /** 策略注册表。 */
  private final ChannelFetchStrategyRegistry registry;
  /** 批量抓取线程池。 */
  private final Executor channelFetchExecutor;

  /**
   * @param registry             策略注册表
   * @param channelFetchExecutor 全局采集线程池
   */
  public FetchFacadeImpl(
      ChannelFetchStrategyRegistry registry,
      @Qualifier("channelFetchExecutor") Executor channelFetchExecutor) {
    this.registry = registry;
    this.channelFetchExecutor = channelFetchExecutor;
  }

  /**
   * 抓取单个渠道并按 type 走策略。
   *
   * @param request 抓取请求
   * @return 统一出站结果；未知类型返回 unsupported_channel_type
   */
  @Override
  public FetchResult fetch(FetchRequest request) {
    if (request == null) {
      return new FetchResult(false, FetchErrorCodes.INVALID_REQUEST, "抓取请求为空", List.of());
    }
    String rawType = request.getType() == null || request.getType().isBlank()
        ? ChannelType.WEB.getValue()
        : request.getType();
    ChannelType type = ChannelType.from(rawType);
    if (type == null) {
      log.warn("不支持的渠道类型 type={}", rawType);
      return new FetchResult(
          false, FetchErrorCodes.UNSUPPORTED_CHANNEL_TYPE, "不支持的渠道类型：" + rawType, List.of());
    }
    ChannelFetchStrategy strategy = registry.get(type);
    if (strategy == null) {
      log.warn("渠道类型未注册策略 type={}", type.getValue());
      return new FetchResult(
          false, FetchErrorCodes.UNSUPPORTED_CHANNEL_TYPE, "未注册策略：" + type.getValue(), List.of());
    }
    ChannelFetchContext context = ChannelFetchContext.from(request, type);
    log.info("开始渠道抓取 type={} url={}", type.getValue(), request.getUrl());
    FetchResult result = strategy.fetch(context);
    if (result.isOk()) {
      int count = result.getItems() == null ? 0 : result.getItems().size();
      log.info("渠道抓取完成 type={} 条目数={}", type.getValue(), count);
    } else {
      log.warn("渠道抓取失败 type={} code={} msg={}",
          type.getValue(), result.getErrorCode(), result.getErrorMessage());
    }
    return result;
  }

  /**
   * 使用全局线程池并行批量抓取。
   *
   * @param requests 请求列表
   * @return 与入参顺序对应的结果
   */
  @Override
  public List<FetchResult> fetchBatch(List<FetchRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      return List.of();
    }
    log.info("批量渠道抓取开始 请求数={}", requests.size());
    List<CompletableFuture<FetchResult>> futures = requests.stream()
        .map(req -> CompletableFuture.supplyAsync(() -> fetch(req), channelFetchExecutor))
        .toList();
    List<FetchResult> results = futures.stream().map(CompletableFuture::join).toList();
    log.info("批量渠道抓取完成 结果数={}", results.size());
    return results;
  }

  /**
   * 连通性探测：成功时最多返回样本条数。
   *
   * @param request 抓取请求
   * @return 探测结果
   */
  @Override
  public FetchResult testConnection(FetchRequest request) {
    FetchResult result = fetch(request);
    if (result.isOk()) {
      return new FetchResult(
          true,
          null,
          null,
          result.getItems().stream().limit(ChannelFetchDefaults.TEST_SAMPLE_LIMIT).toList());
    }
    return result;
  }
}
