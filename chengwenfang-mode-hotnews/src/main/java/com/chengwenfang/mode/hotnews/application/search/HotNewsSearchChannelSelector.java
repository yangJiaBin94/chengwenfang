package com.chengwenfang.mode.hotnews.application.search;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.sourcing.domain.entity.SourceChannelEntity;
import com.chengwenfang.sourcing.facade.dto.view.ChannelView;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 热点联网搜索渠道选择：有密钥的 Tavily 优先于 SearXNG。
 */
@Slf4j
@Component
public class HotNewsSearchChannelSelector {

  /**
   * 从雷达渠道视图中选出单个联网通道。
   *
   * @param channels 主题渠道
   * @return 选中渠道；无则 null
   */
  public ChannelView resolveOne(List<ChannelView> channels) {
    if (CollUtil.isEmpty(channels)) {
      return null;
    }
    ChannelView tavily = null;
    ChannelView searxng = null;
    for (ChannelView channel : channels) {
      if (channel == null || StrUtil.isBlank(channel.getId())) {
        continue;
      }
      String type = StrUtil.trim(channel.getType());
      if (ChannelType.TAVILY.getValue().equalsIgnoreCase(type) && hasTavilyKey(channel.getConfig())) {
        if (tavily == null) {
          tavily = channel;
        }
      } else if (ChannelType.SEARXNG.getValue().equalsIgnoreCase(type) && searxng == null) {
        searxng = channel;
      }
    }
    ChannelView chosen = tavily != null ? tavily : searxng;
    if (chosen != null) {
      log.info("热点联网渠道选定 type={} id={} name={}",
          chosen.getType(), chosen.getId(), chosen.getName());
    }
    return chosen;
  }

  /**
   * 资料补充渠道列表：有可用 Tavily 时仅返回这些 Tavily；否则原列表。
   *
   * @param channels SearXNG/Tavily/GitHub 等
   * @return 实际用于补搜的列表
   */
  public List<SourceChannelEntity> preferTavilyForResearch(List<SourceChannelEntity> channels) {
    if (channels == null || channels.isEmpty()) {
      return List.of();
    }
    List<SourceChannelEntity> tavily = new ArrayList<>();
    for (SourceChannelEntity channel : channels) {
      if (channel == null) {
        continue;
      }
      if (!ChannelType.TAVILY.getValue().equalsIgnoreCase(StrUtil.nullToEmpty(channel.getChannelType()))) {
        continue;
      }
      if (StrUtil.isBlank(channel.getApiKeyCiphertext())) {
        continue;
      }
      tavily.add(channel);
    }
    if (tavily.isEmpty()) {
      return channels;
    }
    log.info("资料补充检测到 Tavily 渠道，优先使用 count={} names={}",
        tavily.size(),
        tavily.stream().map(SourceChannelEntity::getName).toList());
    return tavily;
  }

  /**
   * 列表是否以 Tavily 为主（已由 {@link #preferTavilyForResearch} 收窄）。
   *
   * @param channels 渠道
   * @return 全部为 Tavily 时 true
   */
  public boolean isTavilyOnly(List<SourceChannelEntity> channels) {
    if (CollUtil.isEmpty(channels)) {
      return false;
    }
    for (SourceChannelEntity channel : channels) {
      if (channel == null
          || !ChannelType.TAVILY.getValue().equalsIgnoreCase(StrUtil.nullToEmpty(channel.getChannelType()))) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasTavilyKey(ChannelFetchConfig config) {
    return config != null && StrUtil.isNotBlank(config.getApiKey());
  }
}
