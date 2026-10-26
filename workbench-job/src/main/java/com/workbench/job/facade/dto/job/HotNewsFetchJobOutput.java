package com.workbench.job.facade.dto.job;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.workbench.job.facade.constant.JobTypes#HOT_NEWS_FETCH} 出参。
 * <p>由图执行 toJobOutput 合并主题/运行键而成，开放键集。
 */
public class HotNewsFetchJobOutput extends LinkedHashMap<String, Object> {

  /**
   * 自图输出基 Map 复制，再由调用方 put 附加键。
   *
   * @param base 图 toJobOutput；可空
   * @return 出参实例
   */
  public static HotNewsFetchJobOutput fromGraph(Map<String, Object> base) {
    HotNewsFetchJobOutput output = new HotNewsFetchJobOutput();
    if (base != null) {
      output.putAll(base);
    }
    return output;
  }
}
