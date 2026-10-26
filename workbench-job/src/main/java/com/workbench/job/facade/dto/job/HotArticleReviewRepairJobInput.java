package com.workbench.job.facade.dto.job;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.workbench.job.facade.constant.JobTypes#HOT_ARTICLE_REVIEW_REPAIR} 入参。
 */
public class HotArticleReviewRepairJobInput extends LinkedHashMap<String, Object> {

  /**
   * 从已有 Map 复制构造。
   *
   * @param source 源 Map；可空
   * @return 入参实例
   */
  public static HotArticleReviewRepairJobInput from(Map<String, Object> source) {
    HotArticleReviewRepairJobInput input = new HotArticleReviewRepairJobInput();
    if (source != null) {
      input.putAll(source);
    }
    return input;
  }
}
