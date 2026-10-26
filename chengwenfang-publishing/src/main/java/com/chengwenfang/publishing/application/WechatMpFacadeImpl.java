package com.chengwenfang.publishing.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.facade.constant.ChannelCodes;
import com.chengwenfang.infra.support.SecretCipher;
import com.chengwenfang.publishing.application.model.WechatMpTokenResponse;
import com.chengwenfang.publishing.application.publish.WechatMpChannelPublisher;
import com.chengwenfang.publishing.domain.entity.WechatMpArticleDraftEntity;
import com.chengwenfang.publishing.domain.entity.WechatMpSettingsEntity;
import com.chengwenfang.publishing.facade.WechatMpFacade;
import com.chengwenfang.publishing.facade.dto.request.WechatMpDraftPushRequest;
import com.chengwenfang.publishing.facade.dto.request.WechatMpSettingsUpdateRequest;
import com.chengwenfang.publishing.facade.dto.view.WechatMpConnectionTestResult;
import com.chengwenfang.publishing.facade.dto.view.WechatMpDraftPushResult;
import com.chengwenfang.publishing.facade.dto.view.WechatMpSettingsView;
import com.chengwenfang.publishing.infrastructure.mapper.WechatMpArticleDraftMapper;
import com.chengwenfang.publishing.infrastructure.mapper.WechatMpSettingsMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link WechatMpFacade} 实现：凭证读写、连通性探测；草稿推送委派渠道发布扩展点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMpFacadeImpl implements WechatMpFacade {
  /** 作者最大字数。 */
  private static final int AUTHOR_MAX_CHARS = 16;

  /** 渠道账号 Mapper。 */
  private final WechatMpSettingsMapper settingsMapper;
  /** 发布记录 Mapper。 */
  private final WechatMpArticleDraftMapper articleDraftMapper;
  /** 渠道发布注册表。 */
  private final ChannelPublisherRegistry channelPublishers;
  /** 公众号发布器（连通性测试与 token 缓存）。 */
  private final WechatMpChannelPublisher wechatMpChannelPublisher;
  /** AppSecret 加解密。 */
  private final SecretCipher secretCipher;

  /**
   * 获取公众号配置（不含 AppSecret 明文）。
   *
   * @return 脱敏视图；未初始化时返回空字段
   */
  @Override
  public WechatMpSettingsView getSettings() {
    return toView(loadOrNull());
  }

  /**
   * 更新公众号配置；空白 AppSecret 表示保留原值。
   *
   * @param request 更新请求；可为 null
   * @return 更新后的脱敏视图
   */
  @Override
  @Transactional
  public WechatMpSettingsView updateSettings(WechatMpSettingsUpdateRequest request) {
    WechatMpSettingsUpdateRequest body =
        request == null ? new WechatMpSettingsUpdateRequest() : request;
    WechatMpSettingsEntity entity = loadOrCreate();
    LocalDateTime now = LocalDateTime.now();
    String previousAppId = StrUtil.nullToEmpty(entity.getAppId());
    // 展示名 / AppID / 作者按请求覆盖（允许清空备注与作者）
    if (body.getAccountName() != null) {
      entity.setAccountName(StrUtil.trim(body.getAccountName()));
    }
    if (body.getAppId() != null) {
      entity.setAppId(StrUtil.trim(body.getAppId()));
    }
    if (body.getDefaultAuthor() != null) {
      entity.setDefaultAuthor(limitChars(StrUtil.trim(body.getDefaultAuthor()), AUTHOR_MAX_CHARS));
    }
    // 空白 AppSecret 表示不改密钥；AppID 或密钥变更后清 token 缓存
    boolean secretUpdated =
        StrUtil.isNotBlank(body.getAppSecret()) && !"null".equals(body.getAppSecret());
    if (secretUpdated) {
      entity.setAppSecretCiphertext(secretCipher.encrypt(body.getAppSecret().trim()));
    }
    String nextAppId = StrUtil.nullToEmpty(entity.getAppId());
    if (secretUpdated || !previousAppId.equals(nextAppId)) {
      wechatMpChannelPublisher.clearTokenCache(previousAppId);
      wechatMpChannelPublisher.clearTokenCache(nextAppId);
    }
    entity.setUpdatedAt(now);
    settingsMapper.updateById(entity);
    log.info("已保存公众号配置 appIdConfigured={}", StrUtil.isNotBlank(entity.getAppId()));
    return toView(entity);
  }

  /**
   * 探测凭证能否换取 access_token。
   *
   * @param request 可选草稿凭证；AppID 非空时优先用请求值，Secret 空则回落已保存密钥
   * @return 探测结果
   */
  @Override
  public WechatMpConnectionTestResult testConnection(WechatMpSettingsUpdateRequest request) {
    WechatMpSettingsEntity saved = loadOrNull();
    String appId =
        request != null && StrUtil.isNotBlank(request.getAppId())
            ? request.getAppId().trim()
            : saved == null ? "" : StrUtil.nullToEmpty(saved.getAppId());
    String appSecret =
        request != null && StrUtil.isNotBlank(request.getAppSecret())
            ? request.getAppSecret().trim()
            : decryptSecret(saved);
    if (StrUtil.isBlank(appId) || StrUtil.isBlank(appSecret)) {
      throw new BusinessException(
          ErrorCodes.WECHAT_MP_CONFIG_MISSING, "请先填写并保存 AppID 与 AppSecret", 400);
    }
    // 真实调用稳定版 token 接口，并写入发布器进程内缓存
    WechatMpTokenResponse token = wechatMpChannelPublisher.fetchAndCacheToken(appId, appSecret);
    return WechatMpConnectionTestResult.builder()
        .ok(true)
        .message("连接成功，已取得 access_token（有效约 " + token.getExpiresInSeconds() + " 秒）")
        .build();
  }

  /**
   * 将排版后的文章推送到公众号草稿箱（委派渠道发布器）。
   *
   * @param request          推送请求（Markdown + 主题等）
   * @param imageBytesLoader 按图片 src 加载字节；找不到返回 null
   * @return 推送结果（含 media_id）
   */
  @Override
  public WechatMpDraftPushResult pushDraft(
      WechatMpDraftPushRequest request, Function<String, byte[]> imageBytesLoader) {
    return channelPublishers.require(ChannelCodes.WECHAT_MP).publish(request, imageBytesLoader);
  }

  /**
   * 加载已有配置行；不存在返回 null。
   *
   * @return 实体或 null
   */
  private WechatMpSettingsEntity loadOrNull() {
    List<WechatMpSettingsEntity> rows =
        settingsMapper.selectList(
            new LambdaQueryWrapper<WechatMpSettingsEntity>()
                .eq(WechatMpSettingsEntity::getChannelCode, ChannelCodes.WECHAT_MP)
                .orderByAsc(WechatMpSettingsEntity::getCreatedAt)
                .last("LIMIT 1"));
    return rows == null || rows.isEmpty() ? null : rows.get(0);
  }

  /**
   * 加载或创建唯一配置行。
   *
   * @return 非空实体
   */
  private WechatMpSettingsEntity loadOrCreate() {
    WechatMpSettingsEntity existing = loadOrNull();
    if (existing != null) {
      return existing;
    }
    LocalDateTime now = LocalDateTime.now();
    WechatMpSettingsEntity created =
        WechatMpSettingsEntity.builder()
            .id(IdsUtil.newId())
            .channelCode(ChannelCodes.WECHAT_MP)
            .accountName("")
            .appId("")
            .appSecretCiphertext(null)
            .defaultAuthor("")
            .createdAt(now)
            .updatedAt(now)
            .build();
    settingsMapper.insert(created);
    return created;
  }

  /**
   * 解密账号 AppSecret；无密文返回空串。
   *
   * @param settings 账号实体；可空
   * @return 明文 Secret
   */
  private String decryptSecret(WechatMpSettingsEntity settings) {
    if (settings == null || StrUtil.isBlank(settings.getAppSecretCiphertext())) {
      return "";
    }
    return StrUtil.nullToEmpty(secretCipher.decrypt(settings.getAppSecretCiphertext()));
  }

  /**
   * 查询账号最近一次成功推送（pushed_at 最大）。
   *
   * @param accountId 账号 ID
   * @return 发布记录；无则 null
   */
  private WechatMpArticleDraftEntity findLatestPublication(String accountId) {
    if (StrUtil.isBlank(accountId)) {
      return null;
    }
    return articleDraftMapper.selectOne(
        new LambdaQueryWrapper<WechatMpArticleDraftEntity>()
            .eq(WechatMpArticleDraftEntity::getAccountId, accountId)
            .orderByDesc(WechatMpArticleDraftEntity::getPushedAt)
            .last("LIMIT 1"));
  }

  /**
   * 实体转脱敏视图。
   *
   * @param entity 可为 null
   * @return 视图
   */
  private WechatMpSettingsView toView(WechatMpSettingsEntity entity) {
    if (entity == null) {
      return WechatMpSettingsView.builder()
          .accountName("")
          .appId("")
          .appSecretConfigured(false)
          .defaultAuthor("")
          .build();
    }
    // 最近推送 media_id / 时间由 publications 计算
    WechatMpArticleDraftEntity latest = findLatestPublication(entity.getId());
    return WechatMpSettingsView.builder()
        .accountName(StrUtil.nullToEmpty(entity.getAccountName()))
        .appId(StrUtil.nullToEmpty(entity.getAppId()))
        .appSecretConfigured(StrUtil.isNotBlank(entity.getAppSecretCiphertext()))
        .defaultAuthor(StrUtil.nullToEmpty(entity.getDefaultAuthor()))
        .lastDraftMediaId(latest == null ? null : latest.getExternalId())
        .lastPushedAt(latest == null ? null : latest.getPushedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /**
   * 按 Unicode 码点截断字符串。
   *
   * @param text     原文
   * @param maxChars 最大码点数
   * @return 截断结果
   */
  private String limitChars(String text, int maxChars) {
    String value = StrUtil.nullToEmpty(text).trim();
    if (value.isEmpty() || maxChars <= 0) {
      return "";
    }
    return value.codePoints()
        .limit(maxChars)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }
}
