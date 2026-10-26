package com.chengwenfang.job.facade.dto.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_ARTICLE_REVIEW} /
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_ARTICLE_REVIEW_REVALIDATE} 出参。
 * <p>键序：status → blockingFailed → results。
 */
public class HotArticleReviewJobOutput extends LinkedHashMap<String, Object> {

  /**
   * 组装审稿出参。
   *
   * @param status         状态码
   * @param blockingFailed 是否阻塞失败
   * @param results        规则结果列表（已是 Map 形态）
   * @return 出参实例
   */
  public static HotArticleReviewJobOutput of(
      String status, boolean blockingFailed, List<Map<String, Object>> results) {
    HotArticleReviewJobOutput output = new HotArticleReviewJobOutput();
    output.put("status", status);
    output.put("blockingFailed", blockingFailed);
    output.put("results", results);
    return output;
  }
}
