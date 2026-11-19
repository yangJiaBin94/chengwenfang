package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkRequest;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.spi.KnowledgeChunker;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 结构优先的 Markdown 切块：按 ATX 标题分节，再在节内滑动窗口切分。
 */
@Component
public class MarkdownChunker implements KnowledgeChunker {

  private static final Pattern ATX_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

  @Override
  public boolean supports(KnowledgeChunkProfile chunkProfile) {
    return KnowledgeChunkProfile.MARKDOWN_STRUCTURED == chunkProfile;
  }

  @Override
  public List<KnowledgeChunkPiece> chunk(KnowledgeChunkRequest request) {
    String title = request == null ? "" : StrUtil.nullToEmpty(request.getTitle());
    String body = request == null ? "" : StrUtil.nullToEmpty(request.getText());
    KnowledgeChunkParams params =
        request == null || request.getParams() == null
            ? KnowledgeChunkParams.defaults()
            : request.getParams();
    if (StrUtil.isBlank(body)) {
      return List.of(singlePiece(title, "", "", 0, 0, 0));
    }
    List<Section> sections = splitByHeadings(body);
    List<KnowledgeChunkPiece> result = new ArrayList<>();
    int sectionParentIndex = -1;
    String sectionPath = null;
    for (Section section : sections) {
      if (StrUtil.isBlank(section.body())) {
        continue;
      }
      List<TextWindowSplitter.TextWindow> windows =
          TextWindowSplitter.split(section.body(), params);
      for (TextWindowSplitter.TextWindow window : windows) {
        int absoluteStart = section.bodyStart() + window.startInclusive();
        int absoluteEnd = section.bodyStart() + window.endExclusive();
        String content = window.text().trim();
        if (StrUtil.isBlank(content)) {
          continue;
        }
        // trim 可能缩短两端：在节内重定位
        int localIndex = section.body().indexOf(content, window.startInclusive());
        if (localIndex >= 0) {
          absoluteStart = section.bodyStart() + localIndex;
          absoluteEnd = absoluteStart + content.length();
        }
        int chunkIndex = result.size();
        KnowledgeChunkPiece piece =
            singlePiece(title, section.headingPath(), content, chunkIndex, absoluteStart, absoluteEnd);
        if (sectionParentIndex >= 0 && StrUtil.equals(section.headingPath(), sectionPath)) {
          piece.setParentChunkIndex(sectionParentIndex);
        } else {
          sectionParentIndex = chunkIndex;
          sectionPath = section.headingPath();
        }
        result.add(piece);
      }
    }
    if (result.isEmpty()) {
      String trimmed = body.trim();
      int start = body.indexOf(trimmed);
      if (start < 0) {
        start = 0;
      }
      return List.of(singlePiece(title, "", trimmed, 0, start, start + trimmed.length()));
    }
    return result;
  }

  /**
   * 按 ATX 标题拆分为带路径与正文偏移的分段。
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
      sections.add(new Section("", markdown, 0));
      return sections;
    }
    String[] pathStack = new String[6];
    if (headingSpans.get(0)[0] > 0) {
      String preamble = markdown.substring(0, headingSpans.get(0)[0]);
      if (StrUtil.isNotBlank(preamble.trim())) {
        sections.add(new Section("", preamble, 0));
      }
    }
    for (int index = 0; index < headingSpans.size(); index++) {
      int level = headingMeta.get(index)[0].length();
      String headingText = headingMeta.get(index)[1];
      pathStack[level - 1] = headingText;
      for (int clear = level; clear < 6; clear++) {
        pathStack[clear] = null;
      }
      String path = buildHeadingPath(pathStack, level);
      int bodyStart = headingSpans.get(index)[1];
      int bodyEnd = index + 1 < headingSpans.size() ? headingSpans.get(index + 1)[0] : markdown.length();
      String sectionBody = markdown.substring(bodyStart, bodyEnd);
      sections.add(new Section(path, sectionBody, bodyStart));
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

  private static KnowledgeChunkPiece singlePiece(
      String title,
      String path,
      String content,
      int index,
      int charSpanStart,
      int charSpanEnd) {
    String safeContent = StrUtil.nullToEmpty(content);
    return KnowledgeChunkPiece.builder()
        .chunkIndex(index)
        .headingPath(StrUtil.nullToEmpty(path))
        .content(safeContent)
        .tokenEstimate(safeContent.replaceAll("\\s+", "").length())
        .embeddingText(ChunkEmbeddingTexts.build(title, path, safeContent))
        .charSpanStart(charSpanStart)
        .charSpanEnd(charSpanEnd)
        .build();
  }

  /** Markdown 分段（标题路径 + 节正文 + 正文在全文中的起点）。 */
  private static final class Section {
    private final String headingPath;
    private final String body;
    private final int bodyStart;

    private Section(String headingPath, String body, int bodyStart) {
      this.headingPath = headingPath;
      this.body = body;
      this.bodyStart = bodyStart;
    }

    private String headingPath() {
      return headingPath;
    }

    private String body() {
      return body;
    }

    private int bodyStart() {
      return bodyStart;
    }
  }
}
