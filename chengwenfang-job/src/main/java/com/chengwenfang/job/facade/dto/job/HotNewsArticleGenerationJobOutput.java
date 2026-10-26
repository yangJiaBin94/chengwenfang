package com.chengwenfang.job.facade.dto.job;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_NEWS_ARTICLE_GENERATION} 出参。
 */
public class HotNewsArticleGenerationJobOutput extends LinkedHashMap<String, Object> {

  /**
   * 自图输出基 Map 复制。
   *
   * @param base 图 toJobOutput；可空
   * @return 出参实例
   */
  public static HotNewsArticleGenerationJobOutput fromGraph(Map<String, Object> base) {
    HotNewsArticleGenerationJobOutput output = new HotNewsArticleGenerationJobOutput();
    if (base != null) {
      output.putAll(base);
    }
    return output;
  }
}
