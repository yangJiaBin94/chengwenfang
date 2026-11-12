package com.chengwenfang.ai.facade;

import com.chengwenfang.ai.facade.dto.request.AiSettingsUpdateRequest;
import com.chengwenfang.ai.facade.dto.request.ProviderConfigPatch;
import com.chengwenfang.ai.facade.dto.view.AiSettingsView;
import com.chengwenfang.ai.facade.dto.view.ConnectionTestResult;

/**
 * AI 设置门面：image / vision / embedding 配置读写与连通性测试。
 */
public interface AiSettingsFacade {
  /** 返回 image/vision/embedding 配置的公开视图（不含明文 apiKey）。 */
  AiSettingsView getSettings();

  /**
   * 部分更新 image / vision / embedding 配置；空 apiKey 不覆盖已有密钥。
   *
   * @param request 更新请求
   * @return 更新后的公开 settings
   */
  AiSettingsView updateSettings(AiSettingsUpdateRequest request);

  /**
   * 用前端表单草稿对指定 kind 做连通性探测并记录 model_call；不落库。
   *
   * @param kind  {@code image}、{@code vision} 或 {@code embedding}
   * @param patch 页面当前填写值；apiKey 空白时回落已保存密钥
   * @return 探测结果
   */
  ConnectionTestResult testConnection(String kind, ProviderConfigPatch patch);
}
