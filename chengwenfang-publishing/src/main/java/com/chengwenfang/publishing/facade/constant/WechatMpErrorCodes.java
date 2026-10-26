package com.chengwenfang.publishing.facade.constant;

/**
 * 微信公众号 API 常见 errcode（用于中文操作提示）。
 */
public final class WechatMpErrorCodes {
  /** IP 不在白名单。 */
  public static final int IP_NOT_IN_WHITELIST = 40164;
  /** 稳定版 token 相关 IP 限制。 */
  public static final int STABLE_TOKEN_IP_BLOCKED = 61004;
  /** access_token 无效。 */
  public static final int INVALID_CREDENTIAL = 40001;
  /** AppSecret 无效。 */
  public static final int INVALID_SECRET = 40125;
  /** AppID 无效。 */
  public static final int INVALID_APPID = 40013;
  /** 接口未授权。 */
  public static final int API_UNAUTHORIZED = 48001;
  /** 不合法的媒体文件 id（草稿已被删或不存在）。 */
  public static final int INVALID_MEDIA_ID = 40007;

  private WechatMpErrorCodes() {}
}
