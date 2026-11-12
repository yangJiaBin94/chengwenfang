package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构优先的 Markdown 切块：按 ATX 标题分节，再在节内滑动窗口切分。
 */
public class MarkdownChunker {

  /** 目标块长（字符）。 */
  private static final int TARGET_CHARS = 600;
  /** 最大块长。 */
  private static final int MAX_CHARS = 800;
  /** 相邻块重叠字符数。 */
  private static final int OVERLAP_CHARS = 80;
  /** 过短块合并阈值。 */
  private static final int MIN_MERGE_CHARS = 120;

  private static final Pattern ATX_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

  /**
   * 将 Markdown 正文切成检索块列表。
   *
   * @param title    文档标题（写入嵌入前缀）
   * @param markdown 原始 Markdown
   * @return 切块列表
   */
  public List<KnowledgeChunkPiece> chunk(String title, String markdown) {
    String body = markdown == null ? "" : markdown;
    if (StrUtil.isBlank(body)) {
      return List.of(singlePiece(title, "", "", 0));
    }
    List<Section> sections = splitByHeadings(body);
    List<String> rawPieces = new ArrayList<>();
    List<String> headingPaths = new ArrayList<>();
    for (Section section : sections) {
      String path = section.headingPath();
      String text = section.body();
      if (StrUtil.isBlank(text)) {
        continue;
      }
      List<String> windows = windowText(text);
      for (String window : windows) {
        rawPieces.add(window);
        headingPaths.add(path);
      }
    }
    if (rawPieces.isEmpty()) {
      rawPieces.add(body.trim());
      headingPaths.add("");
    }
    mergeShortPieces(rawPieces, headingPaths);
    return toPieces(title, rawPieces, headingPaths);
  }

  /**
   * 按 ATX 标题拆分为带路径的正文段。
   *
   * @param markdown 全文
   * @return 分段列表
   */
  private static List<Section> splitByHeadings(String markdown) {
    List<Section> sections = new ArrayList<>();
    Matcher matcher = ATX_HEADING.matcher(markdown);
    List<int[]> headingSpans = new ArrayList<>();
    List<String[]> headingMeta = new ArrayList<>();
    while (matcher.find()) {
      headingSpans.add(new int[] {matcher.start(), matcher.end()});
      String hashes = matcher.group(1);
      String headingText = StrUtil.trim(matcher.group(2));
      headingMeta.add(new String[] {hashes, headingText});
    }
    if (headingSpans.isEmpty()) {
      sections.add(new Section("", StrUtil.trim(markdown)));
      return sections;
    }
    int[] levels = new int[6];
    String[] pathStack = new String[6];
    if (headingSpans.get(0)[0] > 0) {
      String preamble = StrUtil.trim(markdown.substring(0, headingSpans.get(0)[0]));
      if (StrUtil.isNotBlank(preamble)) {
        sections.add(new Section("", preamble));
      }
    }
    for (int index = 0; index < headingSpans.size(); index++) {
      int level = headingMeta.get(index)[0].length();
      String headingText = headingMeta.get(index)[1];
      levels[level - 1] = level;
      pathStack[level - 1] = headingText;
      for (int clear = level; clear < 6; clear++) {
        pathStack[clear] = null;
      }
      String path = buildHeadingPath(pathStack, level);
      int bodyStart = headingSpans.get(index)[1];
      int bodyEnd = index + 1 < headingSpans.size() ? headingSpans.get(index + 1)[0] : markdown.length();
      String sectionBody = StrUtil.trim(markdown.substring(bodyStart, bodyEnd));
      sections.add(new Section(path, sectionBody));
    }
    return sections;
  }

  /**
   * 由当前标题栈拼路径。
   *
   * @param stack 标题栈
   * @param depth 当前深度
   * @return 用 {@code >} 连接的路径
   */
  private static String buildHeadingPath(String[] stack, int depth) {
    List<String> parts = new ArrayList<>();
    for (int level = 0; level < depth; level++) {
      if (StrUtil.isNotBlank(stack[level])) {
        parts.add(stack[level]);
      }
    }
    return String.join(" > ", parts);
  }

  /**
   * 节内滑动窗口切分。
   *
   * @param text 节正文
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
      if (end < text.length()) {
        int softBreak = findSoftBreak(text, start, end);
        if (softBreak > start) {
          end = softBreak;
        }
      }
      windows.add(text.substring(start, end).trim());
      if (end >= text.length()) {
        break;
      }
      start = Math.max(start + 1, end - OVERLAP_CHARS);
    }
    return windows;
  }

  /**
   * 在窗口末尾向前找自然断点（段落或句号）。
   *
   * @param text  全文
   * @param start 窗口起点
   * @param end   窗口终点（开区间上界候选）
   * @return 断点位置；无则返回 end
   */
  private static int findSoftBreak(String text, int start, int end) {
    int scanStart = Math.max(start, end - 120);
    for (int position = end - 1; position >= scanStart; position--) {
      char ch = text.charAt(position);
      if (ch == '\n' || ch == '。' || ch == '.' || ch == '！' || ch == '？') {
        return position + 1;
      }
    }
    return end;
  }

  /**
   * 合并过短块到前一块。
   *
   * @param pieces       正文块
   * @param headingPaths 对应标题路径
   */
  private static void mergeShortPieces(List<String> pieces, List<String> headingPaths) {
    int index = 1;
    while (index < pieces.size()) {
      String current = pieces.get(index);
      if (current.length() < MIN_MERGE_CHARS && index > 0) {
        String merged = pieces.get(index - 1) + "\n\n" + current;
        pieces.set(index - 1, merged);
        pieces.remove(index);
        headingPaths.remove(index);
      } else {
        index++;
      }
    }
  }

  /**
   * 组装带嵌入前缀的切块结果。
   *
   * @param title        文档标题
   * @param pieces       正文块
   * @param headingPaths 标题路径
   * @return 切块列表
   */
  private static List<KnowledgeChunkPiece> toPieces(
      String title, List<String> pieces, List<String> headingPaths) {
    List<KnowledgeChunkPiece> result = new ArrayList<>();
    for (int index = 0; index < pieces.size(); index++) {
      String content = pieces.get(index);
      String path = headingPaths.get(index);
      result.add(singlePiece(title, path, content, index));
    }
    return result;
  }

  /**
   * 构造单块并计算嵌入文本。
   *
   * @param title   文档标题
   * @param path    标题路径
   * @param content 块正文
   * @param index   序号
   * @return 切块
   */
  private static KnowledgeChunkPiece singlePiece(String title, String path, String content, int index) {
    String safeTitle = StrUtil.nullToEmpty(title);
    String safePath = StrUtil.nullToEmpty(path);
    String safeContent = StrUtil.nullToEmpty(content);
    StringBuilder embedding = new StringBuilder();
    if (StrUtil.isNotBlank(safeTitle)) {
      embedding.append(safeTitle);
    }
    if (StrUtil.isNotBlank(safePath)) {
      if (!embedding.isEmpty()) {
        embedding.append("\n");
      }
      embedding.append(safePath);
    }
    if (StrUtil.isNotBlank(safeContent)) {
      if (!embedding.isEmpty()) {
        embedding.append("\n\n");
      }
      embedding.append(safeContent);
    }
    int estimate = safeContent.replaceAll("\\s+", "").length();
    return KnowledgeChunkPiece.builder()
        .chunkIndex(index)
        .headingPath(safePath)
        .content(safeContent)
        .tokenEstimate(estimate)
        .embeddingText(embedding.toString())
        .build();
  }

  /** Markdown 分段（标题路径 + 节正文）。 */
  private static final class Section {
    private final String headingPath;
    private final String body;

    private Section(String headingPath, String body) {
      this.headingPath = headingPath;
      this.body = body;
    }

    private String headingPath() {
      return headingPath;
    }

    private String body() {
      return body;
    }
  }
}
