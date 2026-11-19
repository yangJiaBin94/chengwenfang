package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkRequest;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.spi.KnowledgeChunker;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 纯文本切块：按段落聚合后在缓冲区内滑动窗口；短文单块。
 */
@Component
public class PlainTextChunker implements KnowledgeChunker {

  @Override
  public boolean supports(KnowledgeChunkProfile chunkProfile) {
    return KnowledgeChunkProfile.PLAIN == chunkProfile;
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
      return List.of(piece(title, "", 0, 0, 0));
    }
    // 全文不超过 max：单块，避免短图注/短摘过度切分
    if (body.length() <= params.getMaxChars()) {
      String trimmed = body.trim();
      int start = indexOfOrZero(body, trimmed);
      return List.of(piece(title, trimmed, 0, start, start + trimmed.length()));
    }
    List<ParagraphSpan> paragraphs = splitParagraphs(body);
    List<KnowledgeChunkPiece> result = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();
    int bufferStart = -1;
    for (ParagraphSpan paragraph : paragraphs) {
      if (StrUtil.isBlank(paragraph.text())) {
        continue;
      }
      if (buffer.isEmpty()) {
        buffer.append(paragraph.text());
        bufferStart = paragraph.start();
      } else if (buffer.length() + paragraph.text().length() + 2 <= params.getTargetChars()) {
        buffer.append("\n\n").append(paragraph.text());
      } else {
        appendWindows(result, title, body, bufferStart, buffer.toString(), params);
        buffer.setLength(0);
        buffer.append(paragraph.text());
        bufferStart = paragraph.start();
      }
    }
    if (!buffer.isEmpty()) {
      appendWindows(result, title, body, bufferStart, buffer.toString(), params);
    }
    if (result.isEmpty()) {
      String trimmed = body.trim();
      int start = indexOfOrZero(body, trimmed);
      return List.of(piece(title, trimmed, 0, start, start + trimmed.length()));
    }
    // 重排 chunkIndex
    for (int index = 0; index < result.size(); index++) {
      result.get(index).setChunkIndex(index);
    }
    return result;
  }

  private static void appendWindows(
      List<KnowledgeChunkPiece> result,
      String title,
      String fullBody,
      int bufferStart,
      String bufferText,
      KnowledgeChunkParams params) {
    List<TextWindowSplitter.TextWindow> windows = TextWindowSplitter.split(bufferText, params);
    for (TextWindowSplitter.TextWindow window : windows) {
      String content = window.text().trim();
      if (StrUtil.isBlank(content)) {
        continue;
      }
      // 仅在本缓冲窗内定位，避免全文 indexOf 撞上重复段落
      int[] localSpan = CharSpanSupport.spanOf(bufferText, content, window.startInclusive());
      int absoluteStart = bufferStart + localSpan[0];
      int absoluteEnd = bufferStart + localSpan[1];
      if (absoluteEnd > absoluteStart
          && absoluteEnd <= fullBody.length()
          && fullBody.substring(absoluteStart, absoluteEnd).equals(content)) {
        result.add(piece(title, content, result.size(), absoluteStart, absoluteEnd));
      } else {
        int[] fallback = CharSpanSupport.spanOf(fullBody, content, bufferStart);
        result.add(piece(title, content, result.size(), fallback[0], fallback[1]));
      }
    }
  }

  private static List<ParagraphSpan> splitParagraphs(String text) {
    List<ParagraphSpan> paragraphs = new ArrayList<>();
    String[] parts = text.split("\\n\\s*\\n");
    int cursor = 0;
    for (String part : parts) {
      int found = text.indexOf(part, cursor);
      if (found < 0) {
        found = cursor;
      }
      String trimmed = StrUtil.trim(part);
      if (StrUtil.isNotBlank(trimmed)) {
        int local = part.indexOf(trimmed);
        int start = found + Math.max(0, local);
        paragraphs.add(new ParagraphSpan(trimmed, start));
      }
      cursor = found + part.length();
    }
    return paragraphs;
  }

  private static KnowledgeChunkPiece piece(
      String title, String content, int index, int start, int end) {
    String safeContent = StrUtil.nullToEmpty(content);
    return KnowledgeChunkPiece.builder()
        .chunkIndex(index)
        .headingPath("")
        .content(safeContent)
        .tokenEstimate(safeContent.replaceAll("\\s+", "").length())
        .embeddingText(ChunkEmbeddingTexts.build(title, "", safeContent))
        .charSpanStart(start)
        .charSpanEnd(end)
        .build();
  }

  private static int indexOfOrZero(String haystack, String needle) {
    int index = haystack.indexOf(needle);
    return index >= 0 ? index : 0;
  }

  private record ParagraphSpan(String text, int start) {}
}
