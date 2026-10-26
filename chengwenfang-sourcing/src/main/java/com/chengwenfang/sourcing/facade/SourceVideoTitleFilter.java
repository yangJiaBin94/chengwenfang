package com.chengwenfang.sourcing.facade;

import cn.hutool.core.util.StrUtil;

/**
 * 视频标题硬推销启发式（与爬虫 MarketingPageFilter 互补）。
 */
public final class SourceVideoTitleFilter {
  private SourceVideoTitleFilter() {}

  /**
   * 标题是否像口播带货。
   *
   * @param title 标题
   * @return true 表示疑似硬推销
   */
  public static boolean looksLikeHardSellTitle(String title) {
    if (StrUtil.isBlank(title)) {
      return false;
    }
    String text = title.trim();
    return text.matches("(?s).*(赶紧试试|赶紧冲|马上买|同款链接|橱窗|点击领取).*")
        || text.matches("(?s).*(每天.?次).{0,24}(氨糖|软骨素|钙片|补钙|保健品).*")
        || text.matches("(?s).*(氨糖|软骨素).{0,12}(\\+|＋|和).{0,12}(钙|软骨素|氨糖).*赶紧.*");
  }
}
