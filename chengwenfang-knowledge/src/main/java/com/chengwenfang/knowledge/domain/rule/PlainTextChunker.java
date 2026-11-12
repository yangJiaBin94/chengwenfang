package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本切块：按段落聚合后在段内滑动窗口切分。
 */
public class PlainTextChunker {

  /** 目标块长（字符）。 */
  private static final int TARGET_CHARS = 600;
  /** 最大块长。 */
  private static final int MAX_CHARS = 800;
  /** 相邻块重叠字符数。 */
  private static final int OVERLAP_CHARS = 80;
  /** 过短块合并阈值。 */
  private static final int MIN_MERGE_CHARS = 120;

  /**
   * 将纯文本切成检索块列表。
   *
   * @param title 文档标题
   * @param text  正文
   * @return 切块列表
   */
  public List<KnowledgeChunkPiece> chunk(String title, String text) {
    String body = text == null ? "" : text.trim();
    if (StrUtil.isBlank(body)) {
      return List.of(buildPiece(title, "", "", 0));
    }
    List<String> paragraphs = splitParagraphs(body);
    List<String> rawPieces = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();
    for (String paragraph : paragraphs) {
      if (StrUtil.isBlank(paragraph)) {
        continue;
      }
      if (buffer.isEmpty()) {
        buffer.append(paragraph);
      } else if (buffer.length() + paragraph.length() + 2 <= TARGET_CHARS) {
        buffer.append("\n\n").append(paragraph);
      } else {
        rawPieces.addAll(windowText(buffer.toString()));
        buffer.setLength(0);
        buffer.append(paragraph);
      }
    }
    if (!buffer.isEmpty()) {
      rawPieces.addAll(windowText(buffer.toString()));
    }
    if (rawPieces.isEmpty()) {
      rawPieces.add(body);
    }
    mergeShortPieces(rawPieces);
    List<KnowledgeChunkPiece> result = new ArrayList<>();
    for (int index = 0; index < rawPieces.size(); index++) {
      result.add(buildPiece(title, "", rawPieces.get(index), index));
    }
    return result;
  }

  /**
   * 按空行拆段落。
   *
   * @param text 全文
   * @return 段落列表
   */
  private static List<String> splitParagraphs(String text) {
    String[] parts = text.split("\\n\\s*\\n");
    List<String> paragraphs = new ArrayList<>();
    for (String part : parts) {
      String trimmed = StrUtil.trim(part);
      if (StrUtil.isNotBlank(trimmed)) {
        paragraphs.add(trimmed);
      }
    }
    return paragraphs;
  }

  /**
   * 段内滑动窗口。
   *
   * @param text 段落文本
   * @return 窗口列表
   */
  private static List<String> windowText(String text) {
    if (text.length() <= MAX_CHARS) {
      return List.of(text);
    }
    List<String> windows = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(text.length(), start + TARGET_CHARS);
      windows.add(text.substring(start, end).trim());
      if (end >= text.length()) {
        break;
      }
      start = Math.max(start + 1, end - OVERLAP_CHARS);
    }
    return windows;
  }

  /**
   * 合并过短块。
   *
   * @param pieces 正文块
   */
  private static void mergeShortPieces(List<String> pieces) {
    int index = 1;
    while (index < pieces.size()) {
      if (pieces.get(index).length() < MIN_MERGE_CHARS) {
        String merged = pieces.get(index - 1) + "\n\n" + pieces.get(index);
        pieces.set(index - 1, merged);
        pieces.remove(index);
      } else {
        index++;
      }
    }
  }

  /**
   * 构造单块。
   *
   * @param title   标题
   * @param path    标题路径（纯文本无结构时为空）
   * @param content 正文
   * @param index   序号
   * @return 切块
   */
  private static KnowledgeChunkPiece buildPiece(String title, String path, String content, int index) {
    String safeTitle = StrUtil.nullToEmpty(title);
    String safeContent = StrUtil.nullToEmpty(content);
    String embedding = StrUtil.isBlank(safeTitle)
        ? safeContent
        : safeTitle + "\n\n" + safeContent;
    int estimate = safeContent.replaceAll("\\s+", "").length();
    return KnowledgeChunkPiece.builder()
        .chunkIndex(index)
        .headingPath(StrUtil.nullToEmpty(path))
        .content(safeContent)
        .tokenEstimate(estimate)
        .embeddingText(embedding)
        .build();
  }
}
