package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import java.util.List;

/**
 * 将选题参考资料格式化为可拼进 prompt 的文本块。
 */
public final class HotNewsReferencePromptFormatter {
  /** 写入 prompt 的最大条数。 */
  private static final int MAX_ITEMS = 12;
  /** 单条摘要截断长度。 */
  private static final int SUMMARY_LIMIT = 200;

  private HotNewsReferencePromptFormatter() {}

  /**
   * 格式化参考资料块；无条目时返回明确占位说明。
   *
   * @param items 参考资料；可空
   * @return 供诊断/改写拼入的中文块
   */
  public static String formatBlock(List<HotNewsResearchItemView> items) {
    if (CollUtil.isEmpty(items)) {
      return "参考资料：（无可用条目；无法补真实出处时须写明媒体名，禁止编造）\n";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("参考资料（下列 url 仅供核对与佐证截屏，禁止抄进正文；正文出处写成「来源：简称」，禁止裸 URL 与 [文字](url)）：\n");
    int index = 0;
    for (HotNewsResearchItemView item : items) {
      if (item == null || index >= MAX_ITEMS) {
        break;
      }
      index++;
      builder
          .append(index)
          .append(". ")
          .append(StrUtil.blankToDefault(item.getTitle(), "无标题"));
      if (StrUtil.isNotBlank(item.getUrl())) {
        builder.append(" | ").append(item.getUrl().trim());
      } else {
        builder.append(" | （无 url）");
      }
      String summary = StrUtil.firstNonBlank(item.getSummary(), item.getContentSummary());
      if (StrUtil.isNotBlank(summary)) {
        String clipped = summary.length() > SUMMARY_LIMIT
            ? summary.substring(0, SUMMARY_LIMIT)
            : summary;
        builder.append("\n   ").append(clipped.strip());
      }
      builder.append('\n');
    }
    return builder.toString();
  }
}
