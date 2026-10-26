package com.chengwenfang.job.facade.dto.job;

import com.chengwenfang.common.FlexiblePayload;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#COURSE_DRAFT_GENERATION} 入参。
 */
public class CourseDraftGenerationJobInput extends LinkedHashMap<String, Object> {

  /**
   * 从 FlexiblePayload 展平构造。
   *
   * @param payload 自由载荷；可空
   * @return 入参实例
   */
  public static CourseDraftGenerationJobInput from(FlexiblePayload payload) {
    return from(payload == null ? null : payload.mutableMap());
  }

  /**
   * 从已有 Map 复制构造。
   *
   * @param source 源 Map；可空
   * @return 入参实例
   */
  public static CourseDraftGenerationJobInput from(Map<String, Object> source) {
    CourseDraftGenerationJobInput input = new CourseDraftGenerationJobInput();
    if (source != null) {
      input.putAll(source);
    }
    return input;
  }
}
