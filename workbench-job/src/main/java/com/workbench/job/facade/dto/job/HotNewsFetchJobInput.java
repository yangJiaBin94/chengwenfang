package com.workbench.job.facade.dto.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link com.workbench.job.facade.constant.JobTypes#HOT_NEWS_FETCH} 入参。
 * <p>含 themeId/channelIds/hours/fetchRunId 及 enrich 的 workflowId/skillSnapshot 等开放键。
 */
public class HotNewsFetchJobInput extends LinkedHashMap<String, Object> {

  /**
   * 从已有 Map 复制构造。
   *
   * @param source 源 Map；可空
   * @return 入参实例
   */
  public static HotNewsFetchJobInput from(Map<String, Object> source) {
    HotNewsFetchJobInput input = new HotNewsFetchJobInput();
    if (source != null) {
      input.putAll(source);
    }
    return input;
  }

  /**
   * @return 主题 ID
   */
  public String themeId() {
    Object raw = get("themeId");
    return raw == null ? null : String.valueOf(raw);
  }

  /**
   * @return 渠道 ID 列表；非 List 时 null
   */
  @SuppressWarnings("unchecked")
  public List<String> channelIds() {
    Object raw = get("channelIds");
    if (raw instanceof List<?> list) {
      return (List<String>) list;
    }
    return null;
  }

  /**
   * @return 抓取运行 ID
   */
  public String fetchRunId() {
    Object raw = get("fetchRunId");
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
