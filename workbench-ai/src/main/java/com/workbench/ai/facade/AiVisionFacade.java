package com.workbench.ai.facade;

import com.workbench.ai.facade.dto.request.VisionCompleteRequest;
import com.workbench.ai.facade.dto.view.VisionCompleteResult;

/**
 * 多模态看图门面：根据图片与提示词生成文本理解结果。
 */
public interface AiVisionFacade {
  /**
   * 使用 vision 配置做一次图文补全。
   *
   * @param request 含提示词与图片
   * @return 模型输出文本及所用 model / protocol
   */
  VisionCompleteResult complete(VisionCompleteRequest request);
}
