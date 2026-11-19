package com.chengwenfang.ai.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import com.chengwenfang.ai.facade.constant.AiWebSearchConstants;
import java.util.Locale;

/**
 * 联网搜索能力探测：业务侧只问「当前配置是否支持 WebSearch」，不绑死厂商名到 FormalGraph。
 */
public final class AiWebSearchCapability {
  private AiWebSearchCapability() {}

  /**
   * 当前 vision 配置是否支持服务端联网搜索。
   *
   * @param cfg 提供商配置
   * @return 支持则为 true
   */
  public static boolean supports(AiProviderConfigEntity cfg) {
    if (cfg == null || StrUtil.isBlank(cfg.getApiKeyCiphertext())) {
      return false;
    }
    return isDeepSeekEndpoint(cfg.getBaseUrl());
  }

  /**
   * 联网调用应使用的模型名（DeepSeek 强制 flash）。
   *
   * @param cfg 提供商配置
   * @return 模型名；不支持时为空串
   */
  public static String resolveWebSearchModel(AiProviderConfigEntity cfg) {
    if (!supports(cfg)) {
      return "";
    }
    return AiWebSearchConstants.DEEPSEEK_FLASH_MODEL;
  }

  /**
   * baseUrl 是否指向 DeepSeek。
   *
   * @param baseUrl 上游地址
   * @return 匹配则为 true
   */
  public static boolean isDeepSeekEndpoint(String baseUrl) {
    if (StrUtil.isBlank(baseUrl)) {
      return false;
    }
    String normalized = baseUrl.trim().toLowerCase(Locale.ROOT);
    return normalized.contains("deepseek.com") || normalized.contains("api.deepseek");
  }
}
