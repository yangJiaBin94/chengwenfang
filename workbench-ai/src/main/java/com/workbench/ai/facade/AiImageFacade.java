package com.workbench.ai.facade;

import com.workbench.ai.facade.dto.request.ImageGenerateRequest;
import com.workbench.ai.facade.dto.view.ImageGenerateResult;

/**
 * 图像生成门面。
 */
public interface AiImageFacade {
  /**
   * 调用图像模型生成；{@code dryRun=true} 时不请求上游，仅校验配置。
   */
  ImageGenerateResult generate(ImageGenerateRequest request);
}
