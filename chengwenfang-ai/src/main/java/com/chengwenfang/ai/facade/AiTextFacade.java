package com.chengwenfang.ai.facade;

import com.chengwenfang.ai.facade.dto.request.AiSettingsUpdateRequest;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.AiSettingsView;
import com.chengwenfang.ai.facade.dto.view.ConnectionTestResult;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;

/**
 * 文本模型门面：配置读写、连通性测试与文本补全。
 * <p>settings 同时覆盖 text / image / vision 三侧配置。
 */
public interface AiTextFacade {
  /** 返回 text/image/vision 配置的公开视图（不含明文 apiKey）。 */
  AiSettingsView getSettings();

  /**
   * 部分更新 text / image / vision 配置；空 apiKey 不覆盖已有密钥。
   *
   * @return 更新后的公开 settings
   */
  AiSettingsView updateSettings(AiSettingsUpdateRequest request);

  /**
   * 对指定 kind 做连通性探测并记录 model_call。
   *
   * @param kind {@code text}、{@code image} 或 {@code vision}
   */
  ConnectionTestResult testConnection(String kind);

  /** 文本补全。 */
  TextCompleteResult completeText(TextCompleteRequest request);
}
