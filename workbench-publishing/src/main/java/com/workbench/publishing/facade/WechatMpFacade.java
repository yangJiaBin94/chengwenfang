package com.workbench.publishing.facade;

import com.workbench.publishing.facade.dto.request.WechatMpDraftPushRequest;
import com.workbench.publishing.facade.dto.request.WechatMpSettingsUpdateRequest;
import com.workbench.publishing.facade.dto.view.WechatMpConnectionTestResult;
import com.workbench.publishing.facade.dto.view.WechatMpDraftPushResult;
import com.workbench.publishing.facade.dto.view.WechatMpSettingsView;
import java.util.function.Function;

/** 公众号官方 API：凭证配置、连通性探测与推送草稿箱。 */
public interface WechatMpFacade {
  /**
   * 获取公众号配置（不含 AppSecret 明文）。
   *
   * @return 脱敏视图；未初始化时返回空字段
   */
  WechatMpSettingsView getSettings();

  /**
   * 更新公众号配置；空白 AppSecret 表示保留原值。
   *
   * @param request 更新请求；可为 null
   * @return 更新后的脱敏视图
   */
  WechatMpSettingsView updateSettings(WechatMpSettingsUpdateRequest request);

  /**
   * 探测凭证能否换取 access_token。
   *
   * @param request 可选草稿凭证；AppID 非空时优先用请求值，Secret 空则回落已保存密钥
   * @return 探测结果
   */
  WechatMpConnectionTestResult testConnection(WechatMpSettingsUpdateRequest request);

  /**
   * 将排版后的文章推送到公众号草稿箱。
   *
   * @param request          推送请求（Markdown + 主题等）
   * @param imageBytesLoader 按图片 src（相对或绝对路径）加载字节；找不到返回 null
   * @return 推送结果（含 media_id）
   */
  WechatMpDraftPushResult pushDraft(
      WechatMpDraftPushRequest request, Function<String, byte[]> imageBytesLoader);
}
