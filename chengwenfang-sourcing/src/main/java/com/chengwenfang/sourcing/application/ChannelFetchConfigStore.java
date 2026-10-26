package com.chengwenfang.sourcing.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.common.SearchQueryNormalizer;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.infra.support.SecretCipher;
import com.chengwenfang.sourcing.domain.entity.SourceChannelEntity;
import com.chengwenfang.sourcing.domain.entity.SourceChannelValueEntity;
import com.chengwenfang.sourcing.facade.HotNewsChannelQuotaSupport;
import com.chengwenfang.sourcing.facade.constant.SourceChannelValueTypes;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceChannelValueMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 频道抓取配置：列 + {@code source_channel_values} + apiKey 密文 ↔ {@link ChannelFetchConfig}。
 */
@Component
@RequiredArgsConstructor
public class ChannelFetchConfigStore {
  private final SourceChannelValueMapper valueMapper;
  private final SecretCipher secretCipher;

  /**
   * 组装完整配置（含解密后的 apiKey）；无配置时返回空对象（与原 config_json 空一致）。
   *
   * @param channel 频道实体
   * @return 配置；永不为 null
   */
  public ChannelFetchConfig read(SourceChannelEntity channel) {
    if (channel == null || !Boolean.TRUE.equals(channel.getHasConfig())) {
      return new ChannelFetchConfig();
    }
    List<SourceChannelValueEntity> rows = listValues(channel.getId());
    List<String> queries = new ArrayList<>();
    List<String> urls = new ArrayList<>();
    for (SourceChannelValueEntity row : rows) {
      if (row == null || StrUtil.isBlank(row.getValue())) {
        continue;
      }
      if (SourceChannelValueTypes.QUERY.equals(row.getValueType())) {
        queries.add(row.getValue());
      } else if (SourceChannelValueTypes.URL.equals(row.getValueType())) {
        urls.add(row.getValue());
      }
    }
    return ChannelFetchConfig.builder()
        .query(channel.getPrimaryQuery())
        .queries(queries)
        .urls(urls)
        .maxResults(channel.getMaxResults())
        .topic(channel.getSearchTopic())
        .searchDepth(channel.getSearchDepth())
        .apiKey(decryptApiKey(channel.getApiKeyCiphertext()))
        .dateField(channel.getDateField())
        .sort(channel.getSortMode())
        .build();
  }

  /**
   * 将请求配置写入实体列与子表；空 apiKey 时保留 previous 中的密钥。
   *
   * @param channel  频道实体（就地改列字段）
   * @param incoming 前端提交；null 视为清空为无配置
   * @param previous 库中原配置；可空
   */
  @Transactional
  public void write(
      SourceChannelEntity channel, ChannelFetchConfig incoming, ChannelFetchConfig previous) {
    if (channel == null || StrUtil.isBlank(channel.getId())) {
      return;
    }
    ChannelFetchConfig merged = merge(incoming, previous);
    if (isBlankConfig(merged)) {
      clear(channel);
      return;
    }
    channel.setHasConfig(true);
    channel.setPrimaryQuery(merged.getQuery());
    channel.setMaxResults(merged.getMaxResults());
    channel.setSearchTopic(merged.getTopic());
    channel.setSearchDepth(merged.getSearchDepth());
    channel.setDateField(merged.getDateField());
    channel.setSortMode(merged.getSort());
    if (StrUtil.isNotBlank(merged.getApiKey())) {
      channel.setApiKeyCiphertext(secretCipher.encrypt(merged.getApiKey().trim()));
    } else if (previous != null && StrUtil.isNotBlank(previous.getApiKey())) {
      channel.setApiKeyCiphertext(secretCipher.encrypt(previous.getApiKey().trim()));
    } else {
      channel.setApiKeyCiphertext(null);
    }
    replaceValues(channel.getId(), merged);
  }

  /**
   * 清空配置列与子表。
   *
   * @param channel 频道实体
   */
  @Transactional
  public void clear(SourceChannelEntity channel) {
    if (channel == null || StrUtil.isBlank(channel.getId())) {
      return;
    }
    channel.setHasConfig(false);
    channel.setPrimaryQuery(null);
    channel.setMaxResults(null);
    channel.setSearchTopic(null);
    channel.setSearchDepth(null);
    channel.setApiKeyCiphertext(null);
    channel.setDateField(null);
    channel.setSortMode(null);
    valueMapper.delete(new LambdaQueryWrapper<SourceChannelValueEntity>()
        .eq(SourceChannelValueEntity::getChannelId, channel.getId()));
  }

  /**
   * 合并请求与原配置（空 apiKey 不覆盖）。
   *
   * @param incoming 请求
   * @param previous 原配置
   * @return 合并结果
   */
  private ChannelFetchConfig merge(ChannelFetchConfig incoming, ChannelFetchConfig previous) {
    ChannelFetchConfig merged = previous == null ? new ChannelFetchConfig() : copy(previous);
    if (incoming == null) {
      return merged;
    }
    if (incoming.getQuery() != null) {
      merged.setQuery(incoming.getQuery());
    }
    if (incoming.getQueries() != null) {
      merged.setQueries(SearchQueryNormalizer.normalize(incoming.getQueries()));
    }
    if (incoming.getUrls() != null) {
      merged.setUrls(HotNewsChannelQuotaSupport.dedupeUrls(incoming.getUrls()));
    }
    if (incoming.getMaxResults() != null) {
      merged.setMaxResults(incoming.getMaxResults());
    }
    if (incoming.getTopic() != null) {
      merged.setTopic(incoming.getTopic());
    }
    if (incoming.getSearchDepth() != null) {
      merged.setSearchDepth(incoming.getSearchDepth());
    }
    if (incoming.getDateField() != null) {
      merged.setDateField(incoming.getDateField());
    }
    if (incoming.getSort() != null) {
      merged.setSort(incoming.getSort());
    }
    if (StrUtil.isNotBlank(incoming.getApiKey())) {
      merged.setApiKey(incoming.getApiKey().trim());
    }
    return merged;
  }

  /**
   * 全量覆盖列表值子表。
   *
   * @param channelId 频道 id
   * @param config    配置
   */
  private void replaceValues(String channelId, ChannelFetchConfig config) {
    valueMapper.delete(new LambdaQueryWrapper<SourceChannelValueEntity>()
        .eq(SourceChannelValueEntity::getChannelId, channelId));
    int seq = 0;
    if (config.getQueries() != null) {
      for (String query : config.getQueries()) {
        if (StrUtil.isBlank(query)) {
          continue;
        }
        valueMapper.insert(SourceChannelValueEntity.builder()
            .id(IdsUtil.newId())
            .channelId(channelId)
            .valueType(SourceChannelValueTypes.QUERY)
            .seq(seq++)
            .value(query.trim())
            .build());
      }
    }
    seq = 0;
    if (config.getUrls() != null) {
      for (String url : config.getUrls()) {
        if (StrUtil.isBlank(url)) {
          continue;
        }
        valueMapper.insert(SourceChannelValueEntity.builder()
            .id(IdsUtil.newId())
            .channelId(channelId)
            .valueType(SourceChannelValueTypes.URL)
            .seq(seq++)
            .value(url.trim())
            .build());
      }
    }
  }

  /**
   * 按频道加载列表值，按类型与序号排序。
   *
   * @param channelId 频道 id
   * @return 行列表
   */
  private List<SourceChannelValueEntity> listValues(String channelId) {
    if (StrUtil.isBlank(channelId)) {
      return List.of();
    }
    return valueMapper.selectList(new LambdaQueryWrapper<SourceChannelValueEntity>()
        .eq(SourceChannelValueEntity::getChannelId, channelId)
        .orderByAsc(SourceChannelValueEntity::getValueType)
        .orderByAsc(SourceChannelValueEntity::getSeq));
  }

  /**
   * 是否视为无配置（与原 config_json 空一致）。
   *
   * @param config 配置
   * @return true 表示无实质字段
   */
  private static boolean isBlankConfig(ChannelFetchConfig config) {
    if (config == null) {
      return true;
    }
    return StrUtil.isBlank(config.getQuery())
        && CollUtil.isEmpty(config.getQueries())
        && CollUtil.isEmpty(config.getUrls())
        && config.getMaxResults() == null
        && StrUtil.isBlank(config.getTopic())
        && StrUtil.isBlank(config.getSearchDepth())
        && StrUtil.isBlank(config.getApiKey())
        && StrUtil.isBlank(config.getDateField())
        && StrUtil.isBlank(config.getSort());
  }

  /**
   * 解密 apiKey；密文空则空串。
   *
   * @param ciphertext 密文
   * @return 明文；无则空串
   */
  private String decryptApiKey(String ciphertext) {
    if (StrUtil.isBlank(ciphertext)) {
      return "";
    }
    return secretCipher.decrypt(ciphertext);
  }

  /**
   * 浅拷贝配置。
   *
   * @param source 源
   * @return 副本
   */
  private static ChannelFetchConfig copy(ChannelFetchConfig source) {
    return ChannelFetchConfig.builder()
        .query(source.getQuery())
        .queries(source.getQueries() == null ? new ArrayList<>() : new ArrayList<>(source.getQueries()))
        .urls(source.getUrls() == null ? new ArrayList<>() : new ArrayList<>(source.getUrls()))
        .maxResults(source.getMaxResults())
        .topic(source.getTopic())
        .searchDepth(source.getSearchDepth())
        .apiKey(source.getApiKey())
        .dateField(source.getDateField())
        .sort(source.getSort())
        .build();
  }
}
