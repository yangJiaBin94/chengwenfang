package com.chengwenfang.ai.facade;

import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;

/**
 * 多模态补全门面：按 vision 配置做补全；图片可选（无图为纯文本补全，有图为看图理解）。
 */
public interface AiVisionFacade {
  /**
   * 使用多模态配置做一次补全。
   *
   * @param request 含提示词；{@code images} 可空
   * @return 模型输出文本及所用 model / protocol
   */
  VisionCompleteResult complete(VisionCompleteRequest request);
}
