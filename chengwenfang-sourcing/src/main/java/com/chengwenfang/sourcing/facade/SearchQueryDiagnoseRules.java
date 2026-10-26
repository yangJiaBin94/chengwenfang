package com.chengwenfang.sourcing.facade;

import com.chengwenfang.sourcing.facade.constant.SearchQueryDiagnoseKeys;

/**
 * 搜索词 × 网站诊断触发规则（纯函数，便于单测）。
 */
public final class SearchQueryDiagnoseRules {
  private SearchQueryDiagnoseRules() {}

  /**
   * 是否应对该桶写入/强化拉黑。
   *
   * @param skipped 主题过滤丢弃数
   * @param total   该桶原始条数
   * @return true 表示触发
   */
  public static boolean shouldBlock(int skipped, int total) {
    if (skipped < SearchQueryDiagnoseKeys.MIN_SKIPPED || total <= 0) {
      return false;
    }
    return ((double) skipped) / total >= SearchQueryDiagnoseKeys.MIN_SKIP_RATIO;
  }

  /**
   * 构造拉黑原因摘要。
   *
   * @param skipped 丢弃数
   * @param total   总数
   * @return 原因文案
   */
  public static String reason(int skipped, int total) {
    int percent = total <= 0 ? 0 : (int) Math.round(100.0d * skipped / total);
    return "主题过滤偏题 " + skipped + "/" + total + "（" + percent + "%）";
  }
}
