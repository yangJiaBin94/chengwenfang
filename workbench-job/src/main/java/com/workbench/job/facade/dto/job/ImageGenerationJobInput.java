package com.workbench.job.facade.dto.job;

import com.workbench.common.FlexiblePayload;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.workbench.job.facade.constant.JobTypes#IMAGE_GENERATION} 入参。
 */
public class ImageGenerationJobInput extends LinkedHashMap<String, Object> {

  /**
   * 从 FlexiblePayload 展平构造。
   *
   * @param payload 自由载荷；可空
   * @return 入参实例
   */
  public static ImageGenerationJobInput from(FlexiblePayload payload) {
    return from(payload == null ? null : payload.mutableMap());
  }

  /**
   * 从已有 Map 复制构造。
   *
   * @param source 源 Map；可空
   * @return 入参实例
   */
  public static ImageGenerationJobInput from(Map<String, Object> source) {
    ImageGenerationJobInput input = new ImageGenerationJobInput();
    if (source != null) {
      input.putAll(source);
    }
    return input;
  }

  /**
   * @return 提示词；缺省时调用方自行默认
   */
  public String prompt() {
    Object raw = get("prompt");
    return raw == null ? null : String.valueOf(raw);
  }

  /**
   * @return 文章 ID
   */
  public String articleId() {
    Object raw = get("articleId");
    return raw == null ? null : String.valueOf(raw);
  }

  /**
   * @return 文档 ID
   */
  public String documentId() {
    Object raw = get("documentId");
    return raw == null ? null : String.valueOf(raw);
  }
}
