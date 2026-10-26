package com.chengwenfang.job.facade.dto.job;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_NEWS_ARTICLE_GENERATION} 入参。
 */
public class HotNewsArticleGenerationJobInput extends LinkedHashMap<String, Object> {

  /**
   * 从已有 Map 复制构造。
   *
   * @param source 源 Map；可空
   * @return 入参实例
   */
  public static HotNewsArticleGenerationJobInput from(Map<String, Object> source) {
    HotNewsArticleGenerationJobInput input = new HotNewsArticleGenerationJobInput();
    if (source != null) {
      input.putAll(source);
    }
    return input;
  }

  /**
   * @return 文章 ID
   */
  public String articleId() {
    Object raw = get("articleId");
    return raw == null ? null : String.valueOf(raw);
  }

  /**
   * @return 工作流 ID
   */
  public String workflowId() {
    Object raw = get("workflowId");
    return raw == null ? null : String.valueOf(raw);
  }
}
