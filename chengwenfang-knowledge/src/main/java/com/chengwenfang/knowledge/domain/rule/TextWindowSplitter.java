package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;
import java.util.ArrayList;
import java.util.List;

/**
 * 唯一滑窗实现：软断句、重叠、相对原文的字符区间（按字符，非 tokenizer）。
 */
public final class TextWindowSplitter {

  private TextWindowSplitter() {}

  /**
   * 滑窗结果。
   *
   * @param text           窗口正文
   * @param startInclusive 相对 source 的起始（含）
   * @param endExclusive   相对 source 的结束（不含）
   */
  public record TextWindow(String text, int startInclusive, int endExclusive) {}

  /**
   * 将文本按参数切成窗口；不超过 maxChars 时整段一块。
   *
   * @param source 原文（区间相对此串）
   * @param params 滑窗参数
   * @return 窗口列表
   */
  public static List<TextWindow> split(String source, KnowledgeChunkParams params) {
    String text = source == null ? "" : source;
    if (StrUtil.isBlank(text)) {
      return List.of();
    }
    KnowledgeChunkParams safeParams = params == null ? KnowledgeChunkParams.defaults() : params;
    int maxChars = Math.max(1, safeParams.getMaxChars());
    int targetChars = Math.max(1, safeParams.getTargetChars());
    int overlapChars = Math.max(0, safeParams.getOverlapChars());
    if (text.length() <= maxChars) {
      return List.of(new TextWindow(text, 0, text.length()));
    }
    List<TextWindow> windows = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(text.length(), start + targetChars);
      if (end < text.length()) {
        int softBreak = findSoftBreak(text, start, end);
        if (softBreak > start) {
          end = softBreak;
        }
      }
      String piece = text.substring(start, end);
      windows.add(new TextWindow(piece, start, end));
      if (end >= text.length()) {
        break;
      }
      start = Math.max(start + 1, end - overlapChars);
    }
    return mergeShortContiguous(windows, text, safeParams.getMinMergeChars());
  }

  /**
   * 在窗口末向前找自然断点。
   *
   * @param text  全文
   * @param start 窗口起点
   * @param end   候选终点
   * @return 断点；无则返回 end
   */
  static int findSoftBreak(String text, int start, int end) {
    int lookback = KnowledgeChunkConstants.SOFT_BREAK_LOOKBACK;
    int scanStart = Math.max(start, end - lookback);
    for (int position = end - 1; position >= scanStart; position--) {
      char ch = text.charAt(position);
      if (ch == '\n' || ch == '。' || ch == '.' || ch == '！' || ch == '？' || ch == '；') {
        return position + 1;
      }
    }
    return end;
  }

  /**
   * 合并过短相邻窗：取原文连续子串，保证 charSpan 可回溯。
   *
   * @param windows       窗口
   * @param source        原文
   * @param minMergeChars 合并阈值
   * @return 合并后窗口
   */
  static List<TextWindow> mergeShortContiguous(
      List<TextWindow> windows, String source, int minMergeChars) {
    if (windows == null || windows.isEmpty()) {
      return List.of();
    }
    int threshold = Math.max(0, minMergeChars);
    List<TextWindow> merged = new ArrayList<>(windows);
    int index = 1;
    while (index < merged.size()) {
      TextWindow current = merged.get(index);
      if (current.text().length() < threshold && index > 0) {
        TextWindow previous = merged.get(index - 1);
        int start = previous.startInclusive();
        int end = current.endExclusive();
        String content = source.substring(start, end);
        merged.set(index - 1, new TextWindow(content, start, end));
        merged.remove(index);
      } else {
        index++;
      }
    }
    return merged;
  }
}
