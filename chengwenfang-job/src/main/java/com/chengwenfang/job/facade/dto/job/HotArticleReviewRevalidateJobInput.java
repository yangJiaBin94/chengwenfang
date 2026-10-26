package com.chengwenfang.job.facade.dto.job;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_ARTICLE_REVIEW_REVALIDATE} 入参。
 */
public class HotArticleReviewRevalidateJobInput extends LinkedHashMap<String, Object> {

  /**
   * 从已有 Map 复制构造。
   *
   * @param source 源 Map；可空
   * @return 入参实例
   */
  public static HotArticleReviewRevalidateJobInput from(Map<String, Object> source) {
    HotArticleReviewRevalidateJobInput input = new HotArticleReviewRevalidateJobInput();
    if (source != null) {
      input.putAll(source);
    }
    return input;
  }
}
