package com.chengwenfang.job.facade.dto.job;

import cn.hutool.core.bean.BeanUtil;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#IMAGE_GENERATION} 出参。
 * <p>先 beanToMap(GeneratedImageView)，再 put articleId / documentId。
 */
public class ImageGenerationJobOutput extends LinkedHashMap<String, Object> {

  /**
   * 由配图视图与关联 ID 组装。
   *
   * @param imageView  配图视图 bean
   * @param articleId  文章 ID
   * @param documentId 文档 ID；blank 时不写入
   * @return 出参实例
   */
  public static ImageGenerationJobOutput from(Object imageView, String articleId, String documentId) {
    ImageGenerationJobOutput output = new ImageGenerationJobOutput();
    if (imageView != null) {
      Map<String, Object> beanMap = BeanUtil.beanToMap(imageView, new LinkedHashMap<>(), false, true);
      output.putAll(beanMap);
    }
    output.put("articleId", articleId);
    if (documentId != null) {
      output.put("documentId", documentId);
    }
    return output;
  }
}
