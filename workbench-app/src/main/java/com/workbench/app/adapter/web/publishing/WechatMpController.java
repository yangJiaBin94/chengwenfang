package com.workbench.app.adapter.web.publishing;

import cn.hutool.core.util.StrUtil;
import com.workbench.publishing.application.wechat.WechatMpImageBytesResolver;
import com.workbench.common.ApiResult;
import com.workbench.content.facade.ContentFacade;
import com.workbench.publishing.facade.WechatMpFacade;
import com.workbench.publishing.facade.dto.request.WechatMpDraftPushRequest;
import com.workbench.publishing.facade.dto.request.WechatMpSettingsUpdateRequest;
import com.workbench.publishing.facade.dto.view.WechatMpConnectionTestResult;
import com.workbench.publishing.facade.dto.view.WechatMpDraftPushResult;
import com.workbench.publishing.facade.dto.view.WechatMpSettingsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公众号官方 API 配置与推送草稿箱 HTTP 接口。 */
@Tag(name = "公众号配置", description = "AppID/AppSecret 配置、连通性探测与推送草稿箱")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WechatMpController {
  /** 公众号门面。 */
  private final WechatMpFacade wechatMpFacade;
  /** 图片字节解析。 */
  private final WechatMpImageBytesResolver imageBytesResolver;
  /** 内容门面：解析公众号轨产出 id。 */
  private final ContentFacade content;

  /**
   * 获取公众号配置（不含 AppSecret 明文）。
   *
   * @return 脱敏视图
   */
  @Operation(summary = "获取公众号配置")
  @GetMapping("/settings/wechat-mp")
  public ApiResult<WechatMpSettingsView> getSettings() {
    return ApiResult.ok(wechatMpFacade.getSettings());
  }

  /**
   * 更新公众号配置；空白 AppSecret 表示保留原值。
   *
   * @param body 更新请求；可为 null
   * @return 更新后的脱敏视图
   */
  @Operation(summary = "更新公众号配置")
  @PutMapping("/settings/wechat-mp")
  public ApiResult<WechatMpSettingsView> putSettings(
      @Valid @RequestBody(required = false) WechatMpSettingsUpdateRequest body) {
    return ApiResult.ok(wechatMpFacade.updateSettings(body));
  }

  /**
   * 测试公众号凭证连通性（换取 access_token）。
   *
   * @param body 可选草稿凭证；Secret 空则使用已保存密钥
   * @return 探测结果
   */
  @Operation(summary = "测试公众号连通性")
  @PostMapping("/settings/wechat-mp/test")
  public ApiResult<WechatMpConnectionTestResult> test(
      @RequestBody(required = false) WechatMpSettingsUpdateRequest body) {
    return ApiResult.ok(wechatMpFacade.testConnection(body));
  }

  /**
   * 将当前排版文章推送到公众号草稿箱。
   *
   * @param body 推送请求（Markdown、主题、标题等）
   * @return 推送结果（含 media_id）
   */
  @Operation(summary = "推送到公众号草稿箱")
  @PostMapping("/publishing/wechat-mp/draft")
  public ApiResult<WechatMpDraftPushResult> pushDraft(
      @Valid @RequestBody WechatMpDraftPushRequest body) {
    log.info("开始推送公众号草稿 themeVersionId={}", body == null ? null : body.getThemeVersionId());
    if (body != null && StrUtil.isBlank(body.getDocumentId()) && StrUtil.isNotBlank(body.getArticleId())) {
      body.setDocumentId(content.resolvePublishingOutputId(body.getArticleId()));
    }
    WechatMpDraftPushResult result =
        wechatMpFacade.pushDraft(body, imageBytesResolver::resolve);
    return ApiResult.ok(result);
  }
}
