package com.chengwenfang.job.facade.dto.job;

import cn.hutool.core.bean.BeanUtil;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_NEWS_ARTICLE_REWRITE} 出参。
 * <p>先写入文章视图 beanToMap，再追加 instruction / rewritten，保序同原 Map 路径。
 */
public class HotNewsArticleRewriteJobOutput extends LinkedHashMap<String, Object> {

  /**
   * 由文章视图对象与改写指令组装出参。
   *
   * @param articleView 文章视图（任意 bean，字段名与 HotNewsArticleView 一致）
   * @param instruction 改写指令
   * @return 出参 Map 子类
   */
  public static HotNewsArticleRewriteJobOutput from(Object articleView, String instruction) {
    return from(articleView, instruction, null);
  }

  /**
   * 由文章视图、改写指令与插图报告组装出参。
   *
   * @param articleView         文章视图
   * @param instruction         改写指令/建议摘要
   * @param illustrationReuse   插图复用报告；可空
   * @return 出参 Map 子类
   */
  public static HotNewsArticleRewriteJobOutput from(
      Object articleView, String instruction, Object illustrationReuse) {
    HotNewsArticleRewriteJobOutput output = new HotNewsArticleRewriteJobOutput();
    if (articleView != null) {
      Map<String, Object> beanMap = BeanUtil.beanToMap(articleView, new LinkedHashMap<>(), false, true);
      output.putAll(beanMap);
    }
    output.put("instruction", instruction);
    output.put("rewritten", true);
    if (illustrationReuse != null) {
      output.put("illustrationReuse", illustrationReuse);
    }
    return output;
  }
}
