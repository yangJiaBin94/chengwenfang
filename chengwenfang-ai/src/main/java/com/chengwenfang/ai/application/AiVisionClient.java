package com.chengwenfang.ai.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.ai.facade.enums.AiProviderKind;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 多模态看图客户端：按 vision 配置做图文补全，协议分流复用 {@link AiTextClient}。
 */
@Component
@RequiredArgsConstructor
public class AiVisionClient {

  /** 文本客户端：复用补全统一入口与配置加载。 */
  private final AiTextClient textClient;

  /**
   * 使用多模态模型根据图片与提示词做一次补全。
   *
   * @param request 含提示词与至少一张图片
   * @return 模型输出文本及实际使用的 model / protocol
   */
  public VisionCompleteResult complete(VisionCompleteRequest request) {
    AiProviderConfigEntity cfg = textClient.requireKind(AiProviderKind.VISION);
    if (request == null || CollUtil.isEmpty(request.getImages())) {
      throw new BusinessException(ErrorCodes.TEXT_FAILED, "多模态请求缺少图片", 400);
    }
    String prompt = StrUtil.nullToEmpty(request.getPrompt());
    String systemPrompt = request.getSystemPrompt();
    String scene = StrUtil.isBlank(request.getScene()) ? ModelCallScenes.ADHOC : request.getScene();
    Integer timeoutOverride = request.getTimeoutSeconds();
    String text = textClient.completeInternal(
        cfg, systemPrompt, prompt, scene, timeoutOverride, request.getImages());
    return VisionCompleteResult.builder()
        .text(text)
        .model(cfg.getModel())
        .protocol(cfg.getProtocol())
        .build();
  }
}
