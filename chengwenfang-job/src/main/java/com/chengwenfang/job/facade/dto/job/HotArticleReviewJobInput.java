package com.chengwenfang.job.facade.dto.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_ARTICLE_REVIEW} 入参。
 * <p>开放键集（条件 put + enrich），以 LinkedHashMap 保序。
 */
public class HotArticleReviewJobInput extends LinkedHashMap<String, Object> {

  /**
   * 从已有 Map 复制构造。
   *
   * @param source 源 Map；可空
   * @return 入参实例
   */
  public static HotArticleReviewJobInput from(Map<String, Object> source) {
    HotArticleReviewJobInput input = new HotArticleReviewJobInput();
    if (source != null) {
      input.putAll(source);
    }
    return input;
  }

  /**
   * @return 文章 ID；可空
   */
  public String articleId() {
    return stringValue("articleId");
  }

  /**
   * @return 正文；可空
   */
  public String content() {
    return stringValue("content");
  }

  /**
   * @return 内容哈希；可空
   */
  public String contentHash() {
    return stringValue("contentHash");
  }

  /**
   * @return 标准版本；可空
   */
  public String standardVersion() {
    return stringValue("standardVersion");
  }

  /**
   * @return 来源列表；非 List 时返回 null
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> sources() {
    Object raw = get("sources");
    if (raw instanceof List<?> list) {
      return (List<Map<String, Object>>) list;
    }
    return null;
  }

  /**
   * 读取键对应的字符串值。
   *
   * @param key Map 键
   * @return 字符串；键不存在为 null
   */
  private String stringValue(String key) {
    Object raw = get(key);
    return raw == null ? null : String.valueOf(raw);
  }
}
