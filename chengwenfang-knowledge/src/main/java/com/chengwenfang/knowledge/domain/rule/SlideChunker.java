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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 演示文稿切块：按「## 第 N 页」分节；默认一页一块，超长仅页内滑窗；空页跳过。
 * <p>content 仅为页正文（与 Markdown 一致），页标题进 headingPath；charSpan 覆盖 content。
 */
@Component
@RequiredArgsConstructor
public class SlideChunker implements KnowledgeChunker {

  private static final Pattern SLIDE_HEADING = Pattern.compile("(?m)^##\\s+第\\s*\\d+\\s*页\\s*$");

  /** 无页标题时回退的 Plain 切块器（同一 Spring 实例）。 */
  private final PlainTextChunker plainTextChunker;

  @Override
  public boolean supports(KnowledgeChunkProfile chunkProfile) {
    return KnowledgeChunkProfile.SLIDE == chunkProfile;
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
      return List.of(piece(title, "", "", 0, 0, 0));
    }
    Matcher matcher = SLIDE_HEADING.matcher(body);
    List<int[]> spans = new ArrayList<>();
    List<String> headings = new ArrayList<>();
    while (matcher.find()) {
      spans.add(new int[] {matcher.start(), matcher.end()});
      headings.add(StrUtil.trim(matcher.group()));
    }
    if (spans.isEmpty()) {
      KnowledgeChunkRequest plainRequest = KnowledgeChunkRequest.builder()
          .title(title)
          .text(body)
          .mediaKind(request.getMediaKind())
          .chunkProfile(KnowledgeChunkProfile.PLAIN)
          .params(params)
          .build();
      return plainTextChunker.chunk(plainRequest);
    }
    List<KnowledgeChunkPiece> pieces = new ArrayList<>();
    for (int index = 0; index < spans.size(); index++) {
      int contentStart = spans.get(index)[1];
      int contentEnd = index + 1 < spans.size() ? spans.get(index + 1)[0] : body.length();
      String rawContent = body.substring(contentStart, contentEnd);
      int[] trimmedSpan = CharSpanSupport.spanOfTrimmed(rawContent, contentStart);
      String content = body.substring(trimmedSpan[0], trimmedSpan[1]);
      String heading = headings.get(index);
      if (StrUtil.isBlank(content)) {
        continue;
      }
      // 页内滑窗：禁止跨页 overlap；content 不含页标题
      if (content.length() <= params.getMaxChars()) {
        pieces.add(piece(
            title, heading, content, pieces.size(), trimmedSpan[0], trimmedSpan[1]));
        continue;
      }
      List<TextWindowSplitter.TextWindow> windows = TextWindowSplitter.split(content, params);
      for (TextWindowSplitter.TextWindow window : windows) {
        String windowContent = window.text().trim();
        if (StrUtil.isBlank(windowContent)) {
          continue;
        }
        int[] windowSpan = CharSpanSupport.spanOf(content, windowContent, window.startInclusive());
        int absoluteStart = trimmedSpan[0] + windowSpan[0];
        int absoluteEnd = trimmedSpan[0] + windowSpan[1];
        pieces.add(piece(
            title, heading, windowContent, pieces.size(), absoluteStart, absoluteEnd));
      }
    }
    if (pieces.isEmpty()) {
      int[] span = CharSpanSupport.spanOfTrimmed(body, 0);
      String trimmed = body.substring(span[0], span[1]);
      return List.of(piece(title, "", trimmed, 0, span[0], span[1]));
    }
    return pieces;
  }

  private static KnowledgeChunkPiece piece(
      String title,
      String path,
      String content,
      int index,
      int start,
      int end) {
    String safeContent = StrUtil.nullToEmpty(content);
    String safePath = StrUtil.nullToEmpty(path);
    return KnowledgeChunkPiece.builder()
        .chunkIndex(index)
        .headingPath(safePath)
        .content(safeContent)
        .tokenEstimate(safeContent.replaceAll("\\s+", "").length())
        .embeddingText(ChunkEmbeddingTexts.build(title, safePath, safeContent))
        .charSpanStart(start)
        .charSpanEnd(end)
        .build();
  }
}
