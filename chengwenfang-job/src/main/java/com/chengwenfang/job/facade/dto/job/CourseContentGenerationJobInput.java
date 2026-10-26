package com.chengwenfang.job.facade.dto.job;

import com.chengwenfang.common.FlexiblePayload;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#COURSE_CONTENT_GENERATION} 入参。
 * <p>开放图执行键；亦可直接把 {@link FlexiblePayload} 交给 {@code jobs.create}。
 */
public class CourseContentGenerationJobInput extends LinkedHashMap<String, Object> {

  /**
   * 从 FlexiblePayload 展平构造。
   *
   * @param payload 自由载荷；可空
   * @return 入参实例
   */
  public static CourseContentGenerationJobInput from(FlexiblePayload payload) {
    return from(payload == null ? null : payload.mutableMap());
  }

  /**
   * 从已有 Map 复制构造。
   *
   * @param source 源 Map；可空
   * @return 入参实例
   */
  public static CourseContentGenerationJobInput from(Map<String, Object> source) {
    CourseContentGenerationJobInput input = new CourseContentGenerationJobInput();
    if (source != null) {
      input.putAll(source);
    }
    return input;
  }

  /**
   * @return 工作流 ID
   */
  public String workflowId() {
    Object raw = get("workflowId");
    return raw == null ? null : String.valueOf(raw);
  }

  /**
   * @return 课程 ID
   */
  public String courseId() {
    Object raw = get("courseId");
    return raw == null ? null : String.valueOf(raw);
  }
}
