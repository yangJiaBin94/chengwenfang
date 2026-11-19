package com.chengwenfang.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import com.chengwenfang.ai.facade.constant.AiWebSearchConstants;
import org.junit.jupiter.api.Test;

/** {@link AiWebSearchCapability} 端点探测。 */
class AiWebSearchCapabilityTest {

  @Test
  void supports_deepseekBaseUrlWithKey() {
    AiProviderConfigEntity cfg = new AiProviderConfigEntity();
    cfg.setBaseUrl("https://api.deepseek.com");
    cfg.setApiKeyCiphertext("cipher");
    assertThat(AiWebSearchCapability.supports(cfg)).isTrue();
    assertThat(AiWebSearchCapability.resolveWebSearchModel(cfg))
        .isEqualTo(AiWebSearchConstants.DEEPSEEK_FLASH_MODEL);
    assertThat(AiWebSearchCapability.isDeepSeekEndpoint("https://api.deepseek.com/v1")).isTrue();
  }

  @Test
  void supports_falseForOtherProviders() {
    AiProviderConfigEntity cfg = new AiProviderConfigEntity();
    cfg.setBaseUrl("https://api.openai.com/v1");
    cfg.setApiKeyCiphertext("cipher");
    assertThat(AiWebSearchCapability.supports(cfg)).isFalse();
  }

  @Test
  void resolveDeepSeekRootBaseUrl_stripsV1Suffix() {
    assertThat(DeepSeekWebSearchClient.resolveDeepSeekRootBaseUrl("https://api.deepseek.com/v1"))
        .isEqualTo("https://api.deepseek.com");
    assertThat(DeepSeekWebSearchClient.resolveDeepSeekRootBaseUrl("https://api.deepseek.com/v1/"))
        .isEqualTo("https://api.deepseek.com");
    assertThat(DeepSeekWebSearchClient.resolveDeepSeekRootBaseUrl("https://api.deepseek.com"))
        .isEqualTo("https://api.deepseek.com");
  }
}
