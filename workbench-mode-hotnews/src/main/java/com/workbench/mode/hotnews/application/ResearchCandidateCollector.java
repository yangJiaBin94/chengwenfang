package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.crawler.facade.FetchFacade;
import com.workbench.crawler.facade.SearchFacade;
import com.workbench.crawler.facade.constant.ChannelFetchDefaults;
import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import com.workbench.crawler.facade.dto.request.FetchRequest;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.dto.view.FetchedItemView;
import com.workbench.crawler.facade.dto.view.SearchHitView;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.mode.hotnews.application.model.ResearchCandidate;
import com.workbench.mode.hotnews.domain.TopicResearchPlanner;
import com.workbench.sourcing.application.HotNewsChannelService;
import com.workbench.sourcing.domain.entity.SourceChannelEntity;
import com.workbench.sourcing.infrastructure.mapper.SourceChannelMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 调研候选采集：搜索渠道检索与视频时长戳记。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchCandidateCollector {

  /** 信源渠道 Mapper。 */
  private final SourceChannelMapper channelMapper;
  /** 渠道配置协作。 */
  private final HotNewsChannelService channels;
  /** 抓取门面。 */
  private final FetchFacade fetchFacade;
  /** 搜索门面。 */
  private final SearchFacade searchFacade;
  /** 视频解析协作（时长戳记）。 */
  private final HotNewsVideoParseService videoParse;

  public void stampVideoDurations(List<ResearchCandidate> candidates) {
    videoParse.stampVideoDurations(candidates);
  }

  List<SourceChannelEntity> listResearchSearchChannels(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return List.of();
    }
    return channelMapper.selectList(new LambdaQueryWrapper<SourceChannelEntity>()
        .eq(SourceChannelEntity::getThemeId, themeId)
        .eq(SourceChannelEntity::getEnabled, true)
        .in(SourceChannelEntity::getChannelType, List.of(
            ChannelType.SEARXNG.getValue(),
            ChannelType.TAVILY.getValue(),
            ChannelType.GITHUB.getValue()))
        .orderByAsc(SourceChannelEntity::getName));
  }

  /**
   * 按已配置搜索渠道执行单条检索；无可用渠道时回落默认网页搜索（便于本地/测试）。
   *
   * @param channels 主题搜索渠道
   * @param query    检索词（选题主体派生）
   * @param limit    每渠道条数上限
   * @return 合并去重后的命中
   */
  List<SearchHitView> searchViaConfiguredChannels(
      List<SourceChannelEntity> searchChannels, String query, int limit) {
    if (StrUtil.isBlank(query)) {
      return List.of();
    }
    int capped = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 10));
    Map<String, SearchHitView> merged = new LinkedHashMap<>();
    if (searchChannels != null) {
      for (SourceChannelEntity channel : searchChannels) {
        if (channel == null || StrUtil.isBlank(channel.getBaseUrl())) {
          continue;
        }
        ChannelType type = ChannelType.from(channel.getChannelType());
        if (type == null) {
          continue;
        }
        try {
          ChannelFetchConfig config = this.channels.parseConfig(channel);
          // 保留渠道凭证与其它配置，仅覆盖本次选题检索词
          config.setQuery(query);
          config.setQueries(List.of(query));
          int maxResults = config.getMaxResults() == null || config.getMaxResults() <= 0
              ? capped
              : Math.min(config.getMaxResults(), capped);
          config.setMaxResults(maxResults);
          // hours=null：资料补充允许无发布时间的结果（不套用热点采集的强制日期策略）
          FetchResult result = fetchFacade.fetch(FetchRequest.builder()
              .url(channel.getBaseUrl())
              .type(type.getValue())
              .timeoutSeconds(ChannelFetchDefaults.DEFAULT_TIMEOUT_SECONDS)
              .config(config)
              .build());
          if (!result.isOk() || result.getItems() == null) {
            log.warn("资料补充渠道无结果 channel={} type={} code={}",
                channel.getName(), type.getValue(), result.getErrorCode());
            continue;
          }
          for (FetchedItemView item : result.getItems()) {
            if (item == null || StrUtil.isBlank(item.getUrl())) {
              continue;
            }
            String key = TopicResearchPlanner.normalizeUrl(item.getUrl());
            if (StrUtil.isBlank(key) || merged.containsKey(key)) {
              continue;
            }
            merged.put(key, new SearchHitView(
                StrUtil.isBlank(item.getTitle()) ? item.getUrl() : item.getTitle(),
                item.getUrl(),
                item.getSummary() == null ? "" : item.getSummary()));
          }
        } catch (Exception ex) {
          log.warn("资料补充渠道失败 channel={} type={} err={}",
              channel.getName(), channel.getChannelType(), ex.getMessage());
        }
      }
    }
    if (!merged.isEmpty()) {
      return new ArrayList<>(merged.values());
    }
    // 已配置渠道但全部失败/空结果时，同样回落默认网页搜索，避免资料池只剩原始来源
    try {
      log.info("资料补充渠道无命中，回落默认网页搜索 query={} configuredChannels={}",
          query, searchChannels == null ? 0 : searchChannels.size());
      return searchFacade.search(query, capped);
    } catch (Exception ex) {
      log.warn("资料补充默认网页搜索失败 query={} err={}", query, ex.getMessage());
      return List.of();
    }
  }


}
