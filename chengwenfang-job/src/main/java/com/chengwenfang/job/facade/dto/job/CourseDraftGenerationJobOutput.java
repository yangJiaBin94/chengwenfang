package com.chengwenfang.job.facade.dto.job;

import cn.hutool.core.bean.BeanUtil;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#COURSE_DRAFT_GENERATION} 出参。
 * <p>键序：article → courseId。
 */
public class CourseDraftGenerationJobOutput extends LinkedHashMap<String, Object> {

  /**
   * 由文章视图与课程 ID 组装。
   *
   * @param articleView 文章视图 bean
   * @param courseId    课程 ID
   * @return 出参实例
   */
  public static CourseDraftGenerationJobOutput of(Object articleView, String courseId) {
    CourseDraftGenerationJobOutput output = new CourseDraftGenerationJobOutput();
    Map<String, Object> articleMap = articleView == null
        ? new LinkedHashMap<>()
        : BeanUtil.beanToMap(articleView, new LinkedHashMap<>(), false, true);
    output.put("article", articleMap);
    output.put("courseId", courseId);
    return output;
  }
}
