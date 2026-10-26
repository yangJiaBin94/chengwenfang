package com.workbench.job.facade.dto.job;

import cn.hutool.core.bean.BeanUtil;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.workbench.job.facade.constant.JobTypes#WECHAT_ARTICLE_GENERATION} 出参。
 * <p>键序：article → courseId。
 */
public class WechatArticleGenerationJobOutput extends LinkedHashMap<String, Object> {

  /**
   * 由文章视图与课程 ID 组装。
   *
   * @param articleView 文章视图 bean
   * @param courseId    课程 ID
   * @return 出参实例
   */
  public static WechatArticleGenerationJobOutput of(Object articleView, String courseId) {
    WechatArticleGenerationJobOutput output = new WechatArticleGenerationJobOutput();
    Map<String, Object> articleMap = articleView == null
        ? new LinkedHashMap<>()
        : BeanUtil.beanToMap(articleView, new LinkedHashMap<>(), false, true);
    output.put("article", articleMap);
    output.put("courseId", courseId);
    return output;
  }
}
