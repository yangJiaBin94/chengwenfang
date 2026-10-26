package com.workbench.sourcing.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.IdsUtil;
import com.workbench.common.PageQuery;
import com.workbench.crawler.facade.FetchFacade;
import com.workbench.crawler.facade.constant.ChannelFetchDefaults;
import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import com.workbench.crawler.facade.dto.request.FetchRequest;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.sourcing.domain.entity.SourceChannelEntity;
import com.workbench.sourcing.facade.HotNewsChannelQuotaSupport;
import com.workbench.sourcing.facade.constant.HotNewsFetchKeys;
import com.workbench.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import com.workbench.sourcing.facade.dto.request.HotNewsChannelPatchRequest;
import com.workbench.sourcing.facade.dto.request.HotNewsChannelUpsertRequest;
import com.workbench.sourcing.facade.dto.view.ChannelView;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelDeleteView;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelPageView;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelTestView;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelView;
import com.workbench.sourcing.infrastructure.mapper.SourceChannelMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点信源频道：CRUD、连通性探测与 Worker 侧加载。
 * <p>由 {@link HotNewsFacadeImpl} 薄委托，不对外暴露。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsChannelService {

  /** 新建频道时名称缺省值。 */
  private static final String DEFAULT_CHANNEL_NAME = "channel";

  /** 信源频道表。 */
  private final SourceChannelMapper channelMapper;
  /** 抓取配置列/子表/密钥。 */
  private final ChannelFetchConfigStore configStore;
  /** 网页/RSS 抓取门面，用于探测连通性。 */
  private final FetchFacade fetchFacade;

  /**
   * 列出主题下的信源频道，按更新时间倒序。
   *
   * @param themeId 主题 ID；为 null 时不过滤主题（返回全量频道）
   * @return 频道公开视图列表
   */
  public List<HotNewsChannelView> listChannels(String themeId) {
    return channelMapper.selectList(new LambdaQueryWrapper<SourceChannelEntity>()
            .eq(themeId != null, SourceChannelEntity::getThemeId, themeId)
            .orderByDesc(SourceChannelEntity::getUpdatedAt))
        .stream()
        .map(this::channelView)
        .toList();
  }

  /**
   * 分页列出主题下的信源频道；关键字匹配名称或地址，可选类型与启用状态。
   *
   * @param themeId 主题 ID
   * @param query   分页与关键字；null 时按默认页码页大小
   * @param type    频道类型；空白则不过滤
   * @param enabled 启用状态；null 则不过滤
   * @return 当前页数据、筛选总数与主题级启用/停用统计
   */
  public HotNewsChannelPageView pageChannels(
      String themeId, PageQuery query, String type, Boolean enabled) {
    PageQuery normalized = query == null ? new PageQuery().normalized() : query.normalized();
    String keyword = StrUtil.trim(normalized.getKeyword());

    LambdaQueryWrapper<SourceChannelEntity> filterQuery = new LambdaQueryWrapper<SourceChannelEntity>()
        .eq(StrUtil.isNotBlank(themeId), SourceChannelEntity::getThemeId, themeId)
        .eq(StrUtil.isNotBlank(type), SourceChannelEntity::getChannelType, type)
        .eq(enabled != null, SourceChannelEntity::getEnabled, enabled);
    if (StrUtil.isNotBlank(keyword)) {
      filterQuery.and(wrapper -> wrapper
          .like(SourceChannelEntity::getName, keyword)
          .or()
          .like(SourceChannelEntity::getBaseUrl, keyword));
    }

    long total = channelMapper.selectCount(filterQuery);
    List<HotNewsChannelView> items = List.of();
    if (total > 0) {
      int offset = normalized.offset();
      if (offset >= total) {
        int lastPage = (int) Math.max(1, (total + normalized.getPageSize() - 1) / normalized.getPageSize());
        normalized = PageQuery.builder()
            .page(lastPage)
            .pageSize(normalized.getPageSize())
            .keyword(normalized.getKeyword())
            .build()
            .normalized();
        offset = normalized.offset();
      }
      LambdaQueryWrapper<SourceChannelEntity> pageQuery = filterQuery
          .orderByDesc(SourceChannelEntity::getUpdatedAt)
          .last("LIMIT " + normalized.getPageSize() + " OFFSET " + offset);
      items = channelMapper.selectList(pageQuery).stream().map(this::channelView).toList();
    }

    long enabledCount = channelMapper.selectCount(new LambdaQueryWrapper<SourceChannelEntity>()
        .eq(StrUtil.isNotBlank(themeId), SourceChannelEntity::getThemeId, themeId)
        .eq(SourceChannelEntity::getEnabled, true));
    long themeTotal = channelMapper.selectCount(new LambdaQueryWrapper<SourceChannelEntity>()
        .eq(StrUtil.isNotBlank(themeId), SourceChannelEntity::getThemeId, themeId));
    long disabledCount = Math.max(0, themeTotal - enabledCount);

    return HotNewsChannelPageView.builder()
        .items(items)
        .total(total)
        .page(normalized.getPage())
        .pageSize(normalized.getPageSize())
        .enabledCount(enabledCount)
        .disabledCount(disabledCount)
        .build();
  }

  /**
   * 按完整字段创建或覆盖更新频道；缺省字段写入默认值。
   *
   * @param body 全量 upsert 请求；null 按空对象处理；{@code id} 空则新建
   * @return 写入后的频道视图
   */
  @Transactional
  public HotNewsChannelView upsertChannel(HotNewsChannelUpsertRequest body) {
    HotNewsChannelUpsertRequest req = body == null ? new HotNewsChannelUpsertRequest() : body;
    SourceChannelEntity entity = new SourceChannelEntity();
    entity.setId(StrUtil.isBlank(req.getId()) ? IdsUtil.newId() : req.getId());
    entity.setThemeId(StrUtil.blankToDefault(req.getThemeId(), HotNewsFetchKeys.DEFAULT_THEME_ID));
    entity.setName(StrUtil.blankToDefault(req.getName(), DEFAULT_CHANNEL_NAME));
    entity.setChannelType(StrUtil.blankToDefault(req.getType(), ChannelType.RSS.getValue()));
    entity.setBaseUrl(req.getBaseUrl());
    entity.setTrustLevel(0);
    entity.setEnabled(req.getEnabled() == null || Boolean.TRUE.equals(req.getEnabled()));
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    if (channelMapper.selectById(entity.getId()) == null) {
      HotNewsChannelQuotaSupport.assertCanCreate(countChannelsByTheme(entity.getThemeId()));
      ChannelFetchConfig createConfig = prepareConfigForWrite(
          entity.getChannelType(), req.getConfig(), entity.getBaseUrl());
      HotNewsChannelQuotaSupport.assertWebUrlLimit(
          entity.getChannelType(), createConfig, entity.getBaseUrl());
      channelMapper.insert(entity);
      configStore.write(entity, createConfig, null);
      channelMapper.updateById(entity);
    } else {
      SourceChannelEntity existing = channelMapper.selectById(entity.getId());
      ChannelFetchConfig previous = configStore.read(existing);
      ChannelFetchConfig updateConfig = prepareConfigForWrite(
          entity.getChannelType(), req.getConfig(), entity.getBaseUrl());
      if (updateConfig != null) {
        HotNewsChannelQuotaSupport.assertWebUrlLimit(
            entity.getChannelType(), updateConfig, entity.getBaseUrl());
      }
      entity.setCreatedAt(existing.getCreatedAt());
      entity.setHasConfig(existing.getHasConfig());
      entity.setApiKeyCiphertext(existing.getApiKeyCiphertext());
      configStore.write(entity, updateConfig, previous);
      channelMapper.updateById(entity);
    }
    return channelView(entity);
  }

  /**
   * 在指定主题下新建频道（内部转 upsert，默认 enabled=true）。
   *
   * @param themeId 所属主题 ID，写入请求的 themeId
   * @param body    创建请求；null 按空对象；含 name/type/baseUrl/config
   * @return 新建后的频道视图
   */
  @Transactional
  public HotNewsChannelView createChannel(String themeId, HotNewsChannelCreateRequest body) {
    HotNewsChannelCreateRequest req = body == null ? new HotNewsChannelCreateRequest() : body;
    return upsertChannel(HotNewsChannelUpsertRequest.builder()
        .themeId(themeId)
        .name(req.getName())
        .type(req.getType())
        .baseUrl(req.getBaseUrl())
        .enabled(true)
        .config(req.getConfig())
        .build());
  }

  /**
   * 局部更新频道字段；仅对请求中非 null 字段生效。
   *
   * @param channelId 频道主键
   * @param body      补丁请求；null 按空对象（仅刷新 updatedAt）
   * @return 更新后的频道视图
   * @throws BusinessException 频道不存在时（CHANNEL_NOT_FOUND / 404）
   */
  @Transactional
  public HotNewsChannelView patchChannel(String channelId, HotNewsChannelPatchRequest body) {
    SourceChannelEntity channel = channelMapper.selectById(channelId);
    if (channel == null) {
      throw new BusinessException(ErrorCodes.CHANNEL_NOT_FOUND, "频道不存在", 404);
    }
    HotNewsChannelPatchRequest req = body == null ? new HotNewsChannelPatchRequest() : body;
    if (req.getName() != null) {
      channel.setName(req.getName());
    }
    if (req.getType() != null) {
      channel.setChannelType(req.getType());
    }
    if (req.getBaseUrl() != null) {
      channel.setBaseUrl(req.getBaseUrl());
    }
    if (req.getEnabled() != null) {
      boolean turningOn = Boolean.TRUE.equals(req.getEnabled())
          && !Boolean.TRUE.equals(channel.getEnabled());
      if (turningOn) {
        HotNewsChannelQuotaSupport.assertCanCreate(countChannelsByTheme(channel.getThemeId()));
      }
      channel.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
    }
    if (req.getConfig() != null) {
      ChannelFetchConfig previous = configStore.read(channel);
      ChannelFetchConfig incoming = prepareConfigForWrite(
          channel.getChannelType(), req.getConfig(), channel.getBaseUrl());
      HotNewsChannelQuotaSupport.assertWebUrlLimit(
          channel.getChannelType(), incoming, channel.getBaseUrl());
      configStore.write(channel, incoming, previous);
    }
    channel.setUpdatedAt(LocalDateTime.now());
    channelMapper.updateById(channel);
    return channelView(channel);
  }

  /**
   * 软删除频道：将 enabled 置为 false，不物理删除历史条目。
   *
   * @param channelId 频道主键
   * @return 含 id 与删除时间（取 updatedAt）的删除视图
   * @throws BusinessException 频道不存在时（CHANNEL_NOT_FOUND / 404）
   */
  @Transactional
  public HotNewsChannelDeleteView deleteChannel(String channelId) {
    SourceChannelEntity channel = channelMapper.selectById(channelId);
    if (channel == null) {
      throw new BusinessException(ErrorCodes.CHANNEL_NOT_FOUND, "频道不存在", 404);
    }
    LocalDateTime now = LocalDateTime.now();
    channel.setEnabled(false);
    channel.setUpdatedAt(now);
    channelMapper.updateById(channel);
    return HotNewsChannelDeleteView.builder()
        .id(channel.getId())
        .deletedAt(channel.getUpdatedAt() == null ? now : channel.getUpdatedAt())
        .build();
  }

  /**
   * 探测频道连通性：调用抓取门面 testConnection，返回样例条目数与错误信息。
   *
   * @param channelId 频道主键
   * @return 连通性结果视图（ok/success、错误码文案、itemCount、checkedAt）
   * @throws BusinessException 频道不存在时（CHANNEL_NOT_FOUND / 404）
   */
  public HotNewsChannelTestView testChannel(String channelId) {
    SourceChannelEntity channel = channelMapper.selectById(channelId);
    if (channel == null) {
      throw new BusinessException(ErrorCodes.CHANNEL_NOT_FOUND, "频道不存在", 404);
    }
    ChannelFetchConfig config = configStore.read(channel);
    FetchResult result = fetchFacade.testConnection(FetchRequest.builder()
        .url(channel.getBaseUrl())
        .type(channel.getChannelType())
        .timeoutSeconds(ChannelFetchDefaults.TEST_TIMEOUT_SECONDS)
        .hours(ChannelFetchDefaults.TEST_HOURS)
        .config(config)
        .build());
    return HotNewsChannelTestView.builder()
        .ok(result.isOk())
        .success(result.isOk())
        .errorCode(result.getErrorCode())
        .errorMessage(result.getErrorMessage())
        .message(result.getErrorMessage() == null ? "ok" : result.getErrorMessage())
        .itemCount(result.getItems() == null ? 0 : result.getItems().size())
        .checkedAt(LocalDateTime.now())
        .build();
  }

  /**
   * 加载主题下启用频道（Worker 抓取用，含完整 config）。
   *
   * @param themeId    主题 ID
   * @param channelIds 可选白名单；空则主题下全部启用频道
   * @return 频道视图（含明文 apiKey）
   */
  public List<ChannelView> loadChannels(String themeId, List<String> channelIds) {
    LambdaQueryWrapper<SourceChannelEntity> query = new LambdaQueryWrapper<SourceChannelEntity>()
        .eq(SourceChannelEntity::getThemeId, themeId)
        .eq(SourceChannelEntity::getEnabled, true);
    if (channelIds != null && !channelIds.isEmpty()) {
      query.in(SourceChannelEntity::getId, channelIds);
    }
    return channelMapper.selectList(query).stream()
        .map(channel -> ChannelView.builder()
            .id(channel.getId())
            .name(channel.getName())
            .type(channel.getChannelType())
            .baseUrl(channel.getBaseUrl())
            .config(configStore.read(channel))
            .build())
        .toList();
  }

  /**
   * 读取频道完整抓取配置（含解密 apiKey）。
   *
   * @param channel 频道实体
   * @return 配置对象，永不为 null
   */
  public ChannelFetchConfig parseConfig(SourceChannelEntity channel) {
    return configStore.read(channel);
  }

  /**
   * 将频道实体转为对外视图；config 脱敏，credentialConfigured 反映是否已配置密钥。
   *
   * @param channel 频道实体
   * @return 频道视图
   */
  private HotNewsChannelView channelView(SourceChannelEntity channel) {
    ChannelFetchConfig full = configStore.read(channel);
    boolean credentialConfigured = StrUtil.isNotBlank(full.getApiKey())
        || StrUtil.isNotBlank(channel.getApiKeyCiphertext());
    return HotNewsChannelView.builder()
        .id(channel.getId())
        .themeId(channel.getThemeId())
        .name(channel.getName())
        .type(channel.getChannelType())
        .baseUrl(channel.getBaseUrl())
        .enabled(channel.getEnabled())
        .config(toPublicConfig(full))
        .credentialConfigured(credentialConfigured)
        .lastSuccessAt(channel.getLastSuccessAt())
        .createdAt(channel.getCreatedAt())
        .updatedAt(channel.getUpdatedAt())
        .build();
  }

  /**
   * 统计主题下已启用渠道数（归档/停用不占配额，便于腾出名额后新建）。
   *
   * @param themeId 主题 id
   * @return 已启用数量
   */
  private long countChannelsByTheme(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return 0L;
    }
    Long count = channelMapper.selectCount(new LambdaQueryWrapper<SourceChannelEntity>()
        .eq(SourceChannelEntity::getThemeId, themeId)
        .eq(SourceChannelEntity::getEnabled, true));
    return count == null ? 0L : count;
  }

  /**
   * 写入前规范化配置：网页渠道对 urls 去重；{@code config == null} 时原样返回 null。
   *
   * @param type    渠道类型
   * @param config  请求配置；可空
   * @param baseUrl 入口地址
   * @return 规范化配置；入参 null 则 null
   */
  private ChannelFetchConfig prepareConfigForWrite(
      String type, ChannelFetchConfig config, String baseUrl) {
    if (config == null) {
      return null;
    }
    if (!ChannelType.WEB.getValue().equalsIgnoreCase(StrUtil.nullToEmpty(type))) {
      return config;
    }
    ChannelFetchConfig out = ChannelFetchConfig.builder()
        .query(config.getQuery())
        .queries(config.getQueries() == null ? new ArrayList<>() : new ArrayList<>(config.getQueries()))
        .urls(config.getUrls() == null ? new ArrayList<>() : new ArrayList<>(config.getUrls()))
        .maxResults(config.getMaxResults())
        .topic(config.getTopic())
        .searchDepth(config.getSearchDepth())
        .apiKey(config.getApiKey())
        .dateField(config.getDateField())
        .sort(config.getSort())
        .build();
    List<String> urls = HotNewsChannelQuotaSupport.resolveWebUrls(out, baseUrl);
    out.setUrls(new ArrayList<>(urls));
    return out;
  }

  /**
   * 转为对外配置：去掉明文 apiKey，仅用掩码表示已配置。
   *
   * @param config 完整配置
   * @return 可安全回传前端的配置
   */
  private ChannelFetchConfig toPublicConfig(ChannelFetchConfig config) {
    ChannelFetchConfig publicConfig = ChannelFetchConfig.builder()
        .query(config.getQuery())
        .queries(config.getQueries() == null ? new ArrayList<>() : new ArrayList<>(config.getQueries()))
        .urls(config.getUrls() == null ? new ArrayList<>() : new ArrayList<>(config.getUrls()))
        .maxResults(config.getMaxResults())
        .topic(config.getTopic())
        .searchDepth(config.getSearchDepth())
        .dateField(config.getDateField())
        .sort(config.getSort())
        .build();
    if (StrUtil.isNotBlank(config.getApiKey())) {
      publicConfig.setApiKey(maskSecret(config.getApiKey()));
    }
    return publicConfig;
  }

  /**
   * 密钥掩码：保留前缀与末 4 位。
   *
   * @param secret 明文
   * @return 掩码串
   */
  private String maskSecret(String secret) {
    if (secret.length() <= 8) {
      return "****";
    }
    return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
  }
}
