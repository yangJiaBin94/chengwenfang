package com.chengwenfang.publishing.infrastructure.wechat;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.publishing.application.model.WechatMpTokenResponse;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.publishing.facade.constant.WechatMpApiPaths;
import com.chengwenfang.publishing.facade.constant.WechatMpErrorCodes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 微信公众号服务端 HTTP 客户端：token、正文图、封面素材、草稿箱。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatMpApiClient {
  /** 换取稳定版 access_token 的超时（毫秒）。 */
  private static final int TIMEOUT_MS = 30_000;

  /** 工作台配置（含 wechat-mp.api-base）。 */
  private final ChengwenfangProperties properties;

  /**
   * 使用稳定版接口换取 access_token。
   *
   * @param appId     公众号 AppID
   * @param appSecret 公众号 AppSecret
   * @return access_token 与 expires_in（秒）
   */
  public WechatMpTokenResponse fetchStableAccessToken(String appId, String appSecret) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("grant_type", "client_credential");
    body.put("appid", appId);
    body.put("secret", appSecret);
    JSONObject json =
        postJson(apiBase() + WechatMpApiPaths.STABLE_TOKEN, null, body);
    String token = json.getStr("access_token");
    if (StrUtil.isBlank(token)) {
      throw wechatError("换取 access_token 失败", json);
    }
    int expiresIn = json.getInt("expires_in", 7200);
    return WechatMpTokenResponse.builder().accessToken(token).expiresInSeconds(expiresIn).build();
  }

  /**
   * 上传图文消息正文内图片，返回微信 CDN URL。
   *
   * @param accessToken 接口凭证
   * @param imageBytes  图片字节（jpg/png，≤1MB）
   * @param fileName    表单文件名
   * @return 微信返回的 url
   */
  public String uploadContentImage(String accessToken, byte[] imageBytes, String fileName) {
    String url =
        apiBase()
            + WechatMpApiPaths.UPLOAD_IMAGE
            + "?access_token="
            + accessToken;
    try (HttpResponse response =
        HttpRequest.post(url)
            .timeout(TIMEOUT_MS)
            .form("media", imageBytes, StrUtil.blankToDefault(fileName, "image.png"))
            .execute()) {
      JSONObject json = parseBody(response.body());
      String imageUrl = json.getStr("url");
      if (StrUtil.isBlank(imageUrl)) {
        throw wechatError("上传正文图片失败", json);
      }
      return imageUrl;
    }
  }

  /**
   * 上传永久图片素材，用作图文封面 thumb_media_id。
   *
   * @param accessToken 接口凭证
   * @param imageBytes  图片字节
   * @param fileName    表单文件名
   * @return 永久素材 media_id
   */
  public String uploadPermanentImage(String accessToken, byte[] imageBytes, String fileName) {
    String url =
        apiBase()
            + WechatMpApiPaths.ADD_MATERIAL
            + "?access_token="
            + accessToken
            + "&type=image";
    try (HttpResponse response =
        HttpRequest.post(url)
            .timeout(TIMEOUT_MS)
            .form("media", imageBytes, StrUtil.blankToDefault(fileName, "cover.jpg"))
            .execute()) {
      JSONObject json = parseBody(response.body());
      String mediaId = json.getStr("media_id");
      if (StrUtil.isBlank(mediaId)) {
        throw wechatError("上传封面素材失败", json);
      }
      return mediaId;
    }
  }

  /**
   * 新增图文草稿。
   *
   * @param accessToken   接口凭证
   * @param title         标题
   * @param author        作者
   * @param digest        摘要
   * @param contentHtml   正文 HTML（图片须为 uploadimg 返回的 URL）
   * @param thumbMediaId  封面永久素材 ID
   * @return 草稿 media_id
   */
  public String addNewsDraft(
      String accessToken,
      String title,
      String author,
      String digest,
      String contentHtml,
      String thumbMediaId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("articles", List.of(buildNewsArticle(title, author, digest, contentHtml, thumbMediaId)));
    JSONObject json =
        postJson(
            apiBase() + WechatMpApiPaths.DRAFT_ADD + "?access_token=" + accessToken,
            null,
            body);
    String mediaId = json.getStr("media_id");
    if (StrUtil.isBlank(mediaId)) {
      throw wechatError("新增草稿失败", json);
    }
    return mediaId;
  }

  /**
   * 覆盖已有图文草稿中 index=0 的文章（同文复推用）。
   *
   * @param accessToken   接口凭证
   * @param mediaId       已有草稿 media_id
   * @param title         标题
   * @param author        作者
   * @param digest        摘要
   * @param contentHtml   正文 HTML
   * @param thumbMediaId  封面永久素材 ID
   */
  public void updateNewsDraft(
      String accessToken,
      String mediaId,
      String title,
      String author,
      String digest,
      String contentHtml,
      String thumbMediaId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("media_id", mediaId);
    body.put("index", 0);
    body.put("articles", buildNewsArticle(title, author, digest, contentHtml, thumbMediaId));
    postJson(
        apiBase() + WechatMpApiPaths.DRAFT_UPDATE + "?access_token=" + accessToken,
        null,
        body);
  }

  /**
   * 组装单篇图文草稿字段（新增与更新共用）。
   *
   * @param title         标题
   * @param author        作者
   * @param digest        摘要
   * @param contentHtml   正文 HTML
   * @param thumbMediaId  封面素材 ID
   * @return articles 元素
   */
  private Map<String, Object> buildNewsArticle(
      String title, String author, String digest, String contentHtml, String thumbMediaId) {
    Map<String, Object> article = new LinkedHashMap<>();
    article.put("article_type", "news");
    article.put("title", title);
    article.put("author", StrUtil.nullToEmpty(author));
    article.put("digest", StrUtil.nullToEmpty(digest));
    article.put("content", contentHtml);
    article.put("thumb_media_id", thumbMediaId);
    article.put("need_open_comment", 0);
    article.put("only_fans_can_comment", 0);
    return article;
  }

  /**
   * POST JSON 并解析响应；含 errcode 非 0 时抛业务异常。
   *
   * @param url         完整 URL
   * @param accessToken 可选；非空时拼到 query（当前调用方已自行拼接时可传 null）
   * @param body        请求体
   * @return 响应 JSON
   */
  private JSONObject postJson(String url, String accessToken, Map<String, Object> body) {
    String target = url;
    if (StrUtil.isNotBlank(accessToken) && !target.contains("access_token=")) {
      target = target + (target.contains("?") ? "&" : "?") + "access_token=" + accessToken;
    }
    try (HttpResponse response =
        HttpRequest.post(target)
            .timeout(TIMEOUT_MS)
            .header("Content-Type", "application/json; charset=utf-8")
            .body(JSONUtil.toJsonStr(body))
            .execute()) {
      return parseBody(response.body());
    }
  }

  /**
   * 解析微信 JSON 响应；业务 errcode ≠ 0 时抛错。
   *
   * @param raw 响应原文
   * @return JSON 对象
   */
  private JSONObject parseBody(String raw) {
    if (StrUtil.isBlank(raw)) {
      throw new BusinessException(ErrorCodes.WECHAT_MP_API_FAILED, "微信接口返回空响应", 502);
    }
    JSONObject json = JSONUtil.parseObj(raw);
    Integer errCode = json.getInt("errcode");
    if (errCode != null && errCode != 0) {
      throw wechatError("微信接口调用失败", json);
    }
    return json;
  }

  /**
   * 将微信错误码包装为业务异常（不记录 secret）。
   *
   * @param prefix 中文前缀
   * @param json   微信响应
   * @return 业务异常
   */
  private BusinessException wechatError(String prefix, JSONObject json) {
    int errCode = json.getInt("errcode", -1);
    String errMsg = StrUtil.blankToDefault(json.getStr("errmsg"), "未知错误");
    log.warn("微信接口失败 errcode={} errmsg={}", errCode, errMsg);
    // 草稿 media_id 失效时用专用错误码，便于推送层回退为新建
    if (errCode == WechatMpErrorCodes.INVALID_MEDIA_ID) {
      return new BusinessException(
          ErrorCodes.WECHAT_MP_DRAFT_GONE,
          prefix + "（草稿已不存在或 media_id 无效）",
          404);
    }
    String hint = hintForErrorCode(errCode);
    String message =
        hint == null
            ? prefix + "（" + errCode + "：" + errMsg + "）"
            : prefix + "（" + errCode + "：" + errMsg + "）。" + hint;
    return new BusinessException(ErrorCodes.WECHAT_MP_API_FAILED, message, 502);
  }

  /**
   * 解析配置的微信 API 根地址。
   *
   * @return 非空根地址（无尾斜杠）
   */
  private String apiBase() {
    ChengwenfangProperties.WechatMpApiSettings settings = properties.getWechatMp();
    String base = settings == null ? null : settings.getApiBase();
    return StrUtil.blankToDefault(base, "https://api.weixin.qq.com");
  }

  /**
   * 常见错误码的中文操作提示。
   *
   * @param errCode 微信 errcode
   * @return 提示；无则 null
   */
  private String hintForErrorCode(int errCode) {
    if (errCode == WechatMpErrorCodes.IP_NOT_IN_WHITELIST
        || errCode == WechatMpErrorCodes.STABLE_TOKEN_IP_BLOCKED) {
      return "请将本机出口 IP 加入公众平台「IP 白名单」";
    }
    if (errCode == WechatMpErrorCodes.INVALID_CREDENTIAL
        || errCode == WechatMpErrorCodes.INVALID_SECRET
        || errCode == WechatMpErrorCodes.INVALID_APPID) {
      return "请检查 AppID / AppSecret 是否正确";
    }
    if (errCode == WechatMpErrorCodes.API_UNAUTHORIZED) {
      return "当前账号未授权该接口，请在公众平台开发者中心确认权限";
    }
    return null;
  }
}
