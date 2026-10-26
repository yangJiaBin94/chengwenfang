package com.workbench.publishing.application;

import cn.hutool.core.util.StrUtil;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.publishing.facade.spi.ChannelPublisher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 渠道发布器注册表：按 channelCode 查找 {@link ChannelPublisher}。
 */
@Component
public class ChannelPublisherRegistry {

  /** channelCode → 发布器。 */
  private final Map<String, ChannelPublisher> byCode;

  /**
   * @param publishers Spring 注入的全部渠道发布器
   */
  public ChannelPublisherRegistry(List<ChannelPublisher> publishers) {
    Map<String, ChannelPublisher> map = new LinkedHashMap<>();
    for (ChannelPublisher publisher : publishers) {
      map.put(publisher.channelCode(), publisher);
    }
    this.byCode = Map.copyOf(map);
  }

  /**
   * 查找渠道发布器。
   *
   * @param channelCode 渠道 code
   * @return 发布器；未注册为空
   */
  public Optional<ChannelPublisher> find(String channelCode) {
    if (StrUtil.isBlank(channelCode)) {
      return Optional.empty();
    }
    return Optional.ofNullable(byCode.get(channelCode.trim()));
  }

  /**
   * 获取渠道发布器；未注册则抛业务异常。
   *
   * @param channelCode 渠道 code
   * @return 发布器
   */
  public ChannelPublisher require(String channelCode) {
    return find(channelCode).orElseThrow(() -> new BusinessException(
        ErrorCodes.CHANNEL_NOT_FOUND, "未注册的渠道发布器：" + channelCode, 404));
  }
}
