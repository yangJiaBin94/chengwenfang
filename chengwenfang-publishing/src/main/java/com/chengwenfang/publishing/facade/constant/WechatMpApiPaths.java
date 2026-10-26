package com.chengwenfang.publishing.facade.constant;

/**
 * 微信公众号服务端 API 路径常量（相对 {@code chengwenfang.wechat-mp.api-base}）。
 */
public final class WechatMpApiPaths {
  /** 稳定版 access_token。 */
  public static final String STABLE_TOKEN = "/cgi-bin/stable_token";

  /** 上传图文正文内图片。 */
  public static final String UPLOAD_IMAGE = "/cgi-bin/media/uploadimg";

  /** 新增永久素材（封面图）。 */
  public static final String ADD_MATERIAL = "/cgi-bin/material/add_material";

  /** 新增草稿。 */
  public static final String DRAFT_ADD = "/cgi-bin/draft/add";

  /** 更新已有草稿。 */
  public static final String DRAFT_UPDATE = "/cgi-bin/draft/update";

  private WechatMpApiPaths() {}
}
