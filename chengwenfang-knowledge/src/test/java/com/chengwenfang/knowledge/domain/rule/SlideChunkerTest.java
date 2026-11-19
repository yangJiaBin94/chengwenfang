package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkRequest;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link SlideChunker} 按页切与页内窗。 */
class SlideChunkerTest {

  @Test
  void skipsEmptySlidesAndChunksPerPage() {
    SlideChunker chunker = new SlideChunker(new PlainTextChunker());
    String text =
        """
        ## 第 1 页

        封面标题

        ## 第 2 页

        ## 第 3 页

        正文内容
        """;
    List<KnowledgeChunkPiece> pieces = chunker.chunk(request(text));
    assertThat(pieces).hasSize(2);
    assertThat(pieces.get(0).getHeadingPath()).contains("第 1 页");
    assertThat(pieces.get(1).getHeadingPath()).contains("第 3 页");
    assertThat(pieces.get(0).getContent()).isEqualTo("封面标题");
    assertCharSpan(text, pieces.get(0));
    assertCharSpan(text, pieces.get(1));
  }

  @Test
  void oversizedSlideUsesInPageWindows() {
    SlideChunker chunker = new SlideChunker(new PlainTextChunker());
    String longBody = "幻灯正文。".repeat(300);
    String text = "## 第 1 页\n\n" + longBody;
    List<KnowledgeChunkPiece> pieces = chunker.chunk(request(text));
    assertThat(pieces.size()).isGreaterThan(1);
    assertThat(pieces).allMatch(piece -> piece.getHeadingPath().contains("第 1 页"));
  }

  @Test
  void fallsBackToPlainWithoutSlideHeadings() {
    SlideChunker chunker = new SlideChunker(new PlainTextChunker());
    List<KnowledgeChunkPiece> pieces =
        chunker.chunk(request("没有页标题的纯文本内容足够长。".repeat(20)));
    assertThat(pieces).isNotEmpty();
  }

  private static KnowledgeChunkRequest request(String text) {
    return KnowledgeChunkRequest.builder()
        .title("演示")
        .text(text)
        .chunkProfile(KnowledgeChunkProfile.SLIDE)
        .params(KnowledgeChunkParams.defaults())
        .build();
  }

  private static void assertCharSpan(String body, KnowledgeChunkPiece piece) {
    assertThat(piece.getCharSpanStart()).isNotNull();
    assertThat(piece.getCharSpanEnd()).isNotNull();
    assertThat(body.substring(piece.getCharSpanStart(), piece.getCharSpanEnd()))
        .isEqualTo(piece.getContent());
  }
}
