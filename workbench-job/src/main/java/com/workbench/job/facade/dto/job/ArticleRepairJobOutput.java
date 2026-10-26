package com.workbench.job.facade.dto.job;

import cn.hutool.core.bean.BeanUtil;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.workbench.job.facade.constant.JobTypes#ARTICLE_REPAIR} 正常出参。
 * <p>由 {@code RepairResponseView} beanToMap 填充，键序与原路径一致。
 */
public class ArticleRepairJobOutput extends LinkedHashMap<String, Object> {

  /**
   * 由修复结果视图组装出参。
   *
   * @param repairResponse 修复响应视图
   * @return 出参 Map 子类
   */
  public static ArticleRepairJobOutput from(Object repairResponse) {
    ArticleRepairJobOutput output = new ArticleRepairJobOutput();
    if (repairResponse != null) {
      Map<String, Object> beanMap = BeanUtil.beanToMap(repairResponse, new LinkedHashMap<>(), false, true);
      output.putAll(beanMap);
    }
    return output;
  }
}
