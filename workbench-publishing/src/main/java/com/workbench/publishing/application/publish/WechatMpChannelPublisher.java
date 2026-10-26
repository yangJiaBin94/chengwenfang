package com.workbench.publishing.application.publish;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.IdsUtil;
import com.workbench.content.facade.constant.ChannelCodes;
import com.workbench.content.facade.constant.OutputFormatCodes;
import com.workbench.infra.support.SecretCipher;
import com.workbench.publishing.application.OutputRendererRegistry;
import com.workbench.publishing.application.model.WechatMpAccessToken;
import com.workbench.publishing.application.model.WechatMpTokenResponse;
import com.workbench.publishing.domain.entity.WechatMpArticleDraftEntity;
import com.workbench.publishing.domain.entity.WechatMpSettingsEntity;
import com.workbench.publishing.facade.dto.request.WechatMpDraftPushRequest;
import com.workbench.publishing.facade.dto.view.PublishingRenderView;
import com.workbench.publishing.facade.dto.view.WechatMpDraftPushResult;
import com.workbench.publishing.facade.spi.ChannelPublisher;
import com.workbench.publishing.infrastructure.mapper.WechatMpArticleDraftMapper;
import com.workbench.publishing.infrastructure.mapper.WechatMpSettingsMapper;
import com.workbench.publishing.infrastructure.wechat.WechatMpApiClient;
import com.workbench.publishing.infrastructure.wechat.WechatMpImageCompressor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * 微信公众号渠道发布器：渲染 HTML、上传图片、推送草稿箱并回写发布记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatMpChannelPublisher implements ChannelPublisher {

  /** 标题最大字数（微信限制）。 */
  private static final int TITLE_MAX_CHARS = 32;
  /** 作者最大字数。 */
  private static final int AUTHOR_MAX_CHARS = 16;
  /** 摘要最大字数。 */
  private static final int DIGEST_MAX_CHARS = 120;
  /** Markdown 一级/二级标题。 */
  private static final Pattern HEADING_PATTERN =
      Pattern.compile("(?m)^#{1,2}\\s+(.+)$");
  /** Markdown 页眉「摘要：」行（可带引用前缀）。 */
  private static final Pattern DIGEST_LINE_PATTERN =
      Pattern.compile("(?m)^\\s*>?\\s*摘要\\s*[:：]\\s*(.+?)\\s*$");
  /** access_token 提前刷新秒数。 */
  private static final long TOKEN_SKEW_SECONDS = 200L;

  /** 渠道账号 Mapper。 */
  private final WechatMpSettingsMapper settingsMapper;
  /** 发布记录 Mapper。 */
  private final WechatMpArticleDraftMapper articleDraftMapper;
  /** 微信 HTTP 客户端。 */
  private final WechatMpApiClient apiClient;
  /** 产出渲染注册表（文章 → HTML）。 */
  private final OutputRendererRegistry outputRenderers;
  /** AppSecret 加解密。 */
  private final SecretCipher secretCipher;
  /** 进程内 token 缓存（按 AppID）。 */
  private final Map<String, WechatMpAccessToken> tokenCache = new ConcurrentHashMap<>();

  /**
   * {@inheritDoc}
   *
   * @return {@link ChannelCodes#WECHAT_MP}
   */
  @Override
  public String channelCode() {
    return ChannelCodes.WECHAT_MP;
  }

  /**
   * 将排版后的文章推送到公众号草稿箱。
   *
   * @param request          推送请求（Markdown + 主题等）
   * @param imageBytesLoader 按图片 src 加载字节；找不到返回 null
   * @return 推送结果（含 media_id）
   */
  @Override
  public WechatMpDraftPushResult publish(
      WechatMpDraftPushRequest request, Function<String, byte[]> imageBytesLoader) {
    if (request == null || StrUtil.isBlank(request.getMarkdown())) {
      throw new BusinessException(ErrorCodes.WECHAT_MP_DRAFT_FAILED, "推送内容不能为空", 400);
    }
    if (imageBytesLoader == null) {
      throw new BusinessException(ErrorCodes.WECHAT_MP_DRAFT_FAILED, "图片加载器未提供", 500);
    }
    WechatMpSettingsEntity settings = requireConfigured();
    String accessToken = requireAccessToken(settings);
    WechatMpImageCompressor.Session compressSession = new WechatMpImageCompressor.Session();
    try {
      // 按当前主题渲染为公众号 HTML（外呼微信前不占 DB 事务）
      PublishingRenderView rendered =
          outputRenderers.require(OutputFormatCodes.ARTICLE)
              .render(request.getMarkdown(), StrUtil.nullToEmpty(request.getThemeVersionId()));
      String html = StrUtil.nullToEmpty(rendered.getHtml());
      Document document = Jsoup.parseBodyFragment(html);
      Elements images = document.select("img[src]");

      // 先收集原始 src，再上传并改写（封面必须用原始路径读字节）
      List<String> originalSrcList = new ArrayList<>();
      Map<String, String> uploadedUrlBySrc = new LinkedHashMap<>();
      int uploadedCount = 0;
      for (Element image : images) {
        String src = image.attr("src");
        if (StrUtil.isBlank(src)) {
          continue;
        }
        if (!originalSrcList.contains(src)) {
          originalSrcList.add(src);
        }
        if (uploadedUrlBySrc.containsKey(src)) {
          continue;
        }
        WechatMpImageCompressor.PreparedImage prepared =
            prepareImageBytes(compressSession, imageBytesLoader, src, "正文图片");
        String wechatUrl =
            apiClient.uploadContentImage(
                accessToken, prepared.bytes(), uploadFileName(src, prepared));
        uploadedUrlBySrc.put(src, wechatUrl);
        uploadedCount++;
      }
      for (Element image : images) {
        String src = image.attr("src");
        String replaced = uploadedUrlBySrc.get(src);
        if (StrUtil.isNotBlank(replaced)) {
          image.attr("src", replaced);
        }
      }

      // 解析标题 / 摘要 / 作者
      String title =
          limitChars(
              StrUtil.blankToDefault(request.getTitle(), inferTitle(request.getMarkdown())),
              TITLE_MAX_CHARS);
      if (StrUtil.isBlank(title)) {
        throw new BusinessException(ErrorCodes.WECHAT_MP_DRAFT_FAILED, "标题不能为空", 400);
      }
      // 微信草稿标题字段会单独展示，正文去掉重复的首个 h1，避免标题出现两次
      stripLeadingTitleHeading(document, title);
      String author =
          limitChars(
              StrUtil.blankToDefault(request.getAuthor(), settings.getDefaultAuthor()),
              AUTHOR_MAX_CHARS);
      // 摘要优先取「摘要：」字段内容，再写入微信 digest；并从正文去掉该行，避免重复展示
      String digest =
          limitChars(
              StrUtil.blankToDefault(
                  request.getDigest(), inferDigest(request.getMarkdown(), document)),
              DIGEST_MAX_CHARS);
      stripSummaryFromBody(document);
      String contentHtml = document.body().html();

      // 封面：显式 coverSrc 或正文第一张原图
      String coverSrc =
          StrUtil.isNotBlank(request.getCoverSrc())
              ? request.getCoverSrc().trim()
              : (originalSrcList.isEmpty() ? "" : originalSrcList.get(0));
      if (StrUtil.isBlank(coverSrc)) {
        throw new BusinessException(
            ErrorCodes.WECHAT_MP_COVER_REQUIRED, "推送草稿需要封面图，请在正文中至少放置一张图片", 400);
      }
      WechatMpImageCompressor.PreparedImage coverPrepared =
          prepareImageBytes(compressSession, imageBytesLoader, coverSrc, "封面图片");
      String thumbMediaId =
          apiClient.uploadPermanentImage(
              accessToken, coverPrepared.bytes(), uploadFileName(coverSrc, coverPrepared));

      // 有产出绑定且草稿仍在则 update，否则 add；后台删草稿时回退新建
      String articleId = StrUtil.trim(StrUtil.nullToEmpty(request.getArticleId()));
      String outputId = StrUtil.trim(StrUtil.nullToEmpty(request.getDocumentId()));
      String existingMediaId =
          StrUtil.isBlank(outputId) ? "" : findBoundMediaId(outputId, settings.getId());
      boolean updatedExisting = false;
      String mediaId;
      if (StrUtil.isNotBlank(existingMediaId)) {
        try {
          apiClient.updateNewsDraft(
              accessToken, existingMediaId, title, author, digest, contentHtml, thumbMediaId);
          mediaId = existingMediaId;
          updatedExisting = true;
        } catch (BusinessException exception) {
          if (!ErrorCodes.WECHAT_MP_DRAFT_GONE.equals(exception.getCode())) {
            throw exception;
          }
          log.info("绑定草稿已失效，改为新建 outputId={} mediaId={}", outputId, existingMediaId);
          mediaId =
              apiClient.addNewsDraft(accessToken, title, author, digest, contentHtml, thumbMediaId);
        }
      } else {
        mediaId =
            apiClient.addNewsDraft(accessToken, title, author, digest, contentHtml, thumbMediaId);
      }
      LocalDateTime now = LocalDateTime.now();
      // 仅回写发布记录时开短事务
      persistPushResult(settings.getId(), outputId, mediaId, now);
      log.info(
          "已推送公众号草稿 mediaId={} updated={} images={} articleId={} outputId={}",
          mediaId,
          updatedExisting,
          uploadedCount,
          articleId,
          outputId);
      return WechatMpDraftPushResult.builder()
          .mediaId(mediaId)
          .title(title)
          .uploadedImageCount(uploadedCount)
          .pushedAt(now)
          .updatedExisting(updatedExisting)
          .build();
    } finally {
      // 推送结束（成功或失败）删除本轮临时压缩文件，原素材不动
      compressSession.cleanup();
    }
  }

  /**
   * 供设置页连通性测试复用：用指定 AppID/Secret 换取 token 并写入本发布器缓存。
   *
   * @param appId     AppID
   * @param appSecret AppSecret
   * @return token 响应
   */
  public WechatMpTokenResponse fetchAndCacheToken(String appId, String appSecret) {
    WechatMpTokenResponse token = apiClient.fetchStableAccessToken(appId, appSecret);
    cacheToken(appId, token);
    return token;
  }

  /**
   * 凭证变更后清除进程内 token 缓存。
   *
   * @param appId AppID；可空
   */
  public void clearTokenCache(String appId) {
    if (StrUtil.isNotBlank(appId)) {
      tokenCache.remove(appId);
    }
  }

  /**
   * 加载原图字节，必要时质量压缩至 ≤1MB（不缩放、不改原文件）。
   *
   * @param session          压缩会话（登记临时文件）
   * @param imageBytesLoader 字节加载器
   * @param src              图片地址
   * @param label            中文用途标签（正文/封面）
   * @return 可上传的图片准备结果
   */
  private WechatMpImageCompressor.PreparedImage prepareImageBytes(
      WechatMpImageCompressor.Session session,
      Function<String, byte[]> imageBytesLoader,
      String src,
      String label) {
    byte[] bytes = imageBytesLoader.apply(src);
    return session.ensureWithinLimit(bytes, label, src);
  }

  /**
   * 选择上传表单文件名：压缩后统一用 jpg，否则沿用原路径猜测。
   *
   * @param src      原 src
   * @param prepared 压缩结果
   * @return 表单文件名
   */
  private String uploadFileName(String src, WechatMpImageCompressor.PreparedImage prepared) {
    if (prepared != null && prepared.compressed()) {
      return StrUtil.blankToDefault(prepared.suggestedFileName(), "image.jpg");
    }
    return guessFileName(src);
  }

  /**
   * 查询产出已绑定的公众号草稿 media_id。
   *
   * @param outputId  产出 ID
   * @param accountId 渠道账号 ID
   * @return media_id；无绑定返回空串
   */
  private String findBoundMediaId(String outputId, String accountId) {
    WechatMpArticleDraftEntity binding = articleDraftMapper.selectOne(
        new LambdaQueryWrapper<WechatMpArticleDraftEntity>()
            .eq(WechatMpArticleDraftEntity::getOutputId, outputId)
            .eq(WechatMpArticleDraftEntity::getAccountId, accountId)
            .last("LIMIT 1"));
    return binding == null ? "" : StrUtil.nullToEmpty(binding.getExternalId());
  }

  /**
   * 按产出 upsert 发布记录（不包住微信外呼）。
   *
   * @param accountId 渠道账号 ID
   * @param outputId  产出 ID；空则跳过
   * @param mediaId   微信草稿 media_id
   * @param pushedAt  推送时间
   */
  private void persistPushResult(
      String accountId, String outputId, String mediaId, LocalDateTime pushedAt) {
    if (StrUtil.isBlank(outputId) || StrUtil.isBlank(mediaId) || StrUtil.isBlank(accountId)) {
      return;
    }
    WechatMpArticleDraftEntity existing = articleDraftMapper.selectOne(
        new LambdaQueryWrapper<WechatMpArticleDraftEntity>()
            .eq(WechatMpArticleDraftEntity::getOutputId, outputId)
            .eq(WechatMpArticleDraftEntity::getAccountId, accountId)
            .last("LIMIT 1"));
    if (existing == null) {
      articleDraftMapper.insert(
          WechatMpArticleDraftEntity.builder()
              .id(IdsUtil.newId())
              .outputId(outputId)
              .accountId(accountId)
              .externalId(mediaId)
              .pushedAt(pushedAt)
              .createdAt(pushedAt)
              .updatedAt(pushedAt)
              .build());
      return;
    }
    existing.setExternalId(mediaId);
    existing.setPushedAt(pushedAt);
    existing.setUpdatedAt(pushedAt);
    articleDraftMapper.updateById(existing);
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
   * 要求已配置 AppID 与 AppSecret。
   *
   * @return 配置实体
   */
  private WechatMpSettingsEntity requireConfigured() {
    WechatMpSettingsEntity settings = loadOrNull();
    if (settings == null
        || StrUtil.isBlank(settings.getAppId())
        || StrUtil.isBlank(decryptSecret(settings))) {
      throw new BusinessException(
          ErrorCodes.WECHAT_MP_CONFIG_MISSING, "请先在设置页「公众号配置」中填写 AppID 与 AppSecret", 400);
    }
    return settings;
  }

  /**
   * 取得可用 access_token（带内存缓存）。
   *
   * @param settings 已配置凭证
   * @return access_token
   */
  private String requireAccessToken(WechatMpSettingsEntity settings) {
    String appId = settings.getAppId();
    WechatMpAccessToken cached = tokenCache.get(appId);
    if (cached != null
        && StrUtil.isNotBlank(cached.getAccessToken())
        && cached.getExpiresAt() != null
        && cached.getExpiresAt().isAfter(LocalDateTime.now())) {
      return cached.getAccessToken();
    }
    WechatMpTokenResponse token =
        apiClient.fetchStableAccessToken(appId, decryptSecret(settings));
    cacheToken(appId, token);
    return token.getAccessToken();
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
   * 写入 token 缓存（提前 {@link #TOKEN_SKEW_SECONDS} 秒过期）。
   *
   * @param appId AppID
   * @param token 接口响应
   */
  private void cacheToken(String appId, WechatMpTokenResponse token) {
    long ttl = Math.max(60L, token.getExpiresInSeconds() - TOKEN_SKEW_SECONDS);
    tokenCache.put(
        appId,
        WechatMpAccessToken.builder()
            .accessToken(token.getAccessToken())
            .expiresAt(LocalDateTime.now().plusSeconds(ttl))
            .build());
  }

  /**
   * 去掉正文开头与草稿标题重复的一级标题（微信会单独渲染 title 字段）。
   *
   * @param document 渲染后的 HTML 文档
   * @param title    即将写入草稿的标题
   */
  public static void stripLeadingTitleHeading(Document document, String title) {
    if (document == null || StrUtil.isBlank(title)) {
      return;
    }
    Element firstHeading = document.body().selectFirst("h1");
    if (firstHeading == null) {
      return;
    }
    String headingText = StrUtil.nullToEmpty(firstHeading.text()).trim();
    String titleText = title.trim();
    if (headingText.isEmpty()) {
      return;
    }
    if (headingText.equals(titleText)
        || headingText.contains(titleText)
        || titleText.contains(headingText)) {
      firstHeading.remove();
    }
  }

  /**
   * 从 Markdown 推断标题（首个标题行，否则首行非空文本）。
   *
   * @param markdown 原文
   * @return 标题；可能为空
   */
  private String inferTitle(String markdown) {
    Matcher matcher = HEADING_PATTERN.matcher(StrUtil.nullToEmpty(markdown));
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    for (String line : StrUtil.nullToEmpty(markdown).split("\\R")) {
      String trimmed = line.trim();
      if (StrUtil.isNotBlank(trimmed) && !trimmed.startsWith("![")) {
        return trimmed.replaceAll("^#+\\s*", "");
      }
    }
    return "未命名文章";
  }

  /**
   * 推断公众号 digest：优先 Markdown「摘要：」行，其次 HTML 中以「摘要：」开头的段落。
   *
   * @param markdown 原稿 Markdown
   * @param document 渲染 HTML
   * @return 摘要正文（不含「摘要：」前缀）；找不到则空串
   */
  public static String inferDigest(String markdown, Document document) {
    Matcher markdownMatcher = DIGEST_LINE_PATTERN.matcher(StrUtil.nullToEmpty(markdown));
    if (markdownMatcher.find()) {
      return markdownMatcher.group(1).trim();
    }
    if (document != null) {
      for (Element element : document.body().select("p, li, section, div, span")) {
        String text = StrUtil.nullToEmpty(element.ownText()).trim();
        if (StrUtil.isBlank(text)) {
          text = StrUtil.nullToEmpty(element.text()).trim();
        }
        if (text.startsWith("摘要：") || text.startsWith("摘要:")) {
          return text.replaceFirst("^摘要\\s*[:：]\\s*", "").trim();
        }
      }
    }
    return "";
  }

  /**
   * 从正文中移除「摘要：…」段落，避免与微信 digest 字段重复。
   *
   * @param document 渲染 HTML
   */
  public static void stripSummaryFromBody(Document document) {
    if (document == null) {
      return;
    }
    for (Element element : new ArrayList<>(document.body().select("p, li"))) {
      String text = StrUtil.nullToEmpty(element.text()).trim();
      if (text.startsWith("摘要：") || text.startsWith("摘要:")) {
        element.remove();
      }
    }
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

  /**
   * 从 src 猜测上传文件名。
   *
   * @param src 图片地址
   * @return 文件名
   */
  private String guessFileName(String src) {
    String path = StrUtil.nullToEmpty(src);
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    int slash = path.lastIndexOf('/');
    String name = slash >= 0 ? path.substring(slash + 1) : path;
    if (StrUtil.isBlank(name) || !name.contains(".")) {
      return "image.png";
    }
    return name;
  }
}
