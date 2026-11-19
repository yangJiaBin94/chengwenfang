package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkRequest;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link MarkdownChunker} 单元测试。 */
class MarkdownChunkerTest {

  @Test
  void chunk_splitsByHeadingsWithOverlap() {
    String markdown =
        """
        # 第一章 入门

        """
            + repeatChar('A', 1500)
            + """

        # 第二章 另一节

        """
            + repeatChar('B', 200);

    MarkdownChunker chunker = new MarkdownChunker();
    List<KnowledgeChunkPiece> pieces = chunker.chunk(request("测试文档", markdown));

    assertThat(pieces).hasSizeGreaterThan(1);
    assertThat(pieces).anyMatch(piece -> piece.getHeadingPath().contains("第一章"));
    assertThat(pieces).anyMatch(piece -> piece.getHeadingPath().contains("第二章"));
    assertThat(pieces.get(0).getCharSpanStart()).isNotNull();
    assertThat(pieces.get(0).getCharSpanEnd()).isGreaterThan(pieces.get(0).getCharSpanStart());

    boolean overlapFound = false;
    for (int index = 1; index < pieces.size(); index++) {
      String previous = pieces.get(index - 1).getContent();
      String current = pieces.get(index).getContent();
      if (previous.length() >= 80 && current.length() >= 80
          && pieces.get(index - 1).getHeadingPath().equals(pieces.get(index).getHeadingPath())) {
        String tail = previous.substring(previous.length() - 40);
        if (current.contains(tail)) {
          overlapFound = true;
          break;
        }
      }
    }
    assertThat(overlapFound).as("同节相邻块应存在重叠").isTrue();

    KnowledgeChunkPiece firstChapterChild = null;
    for (KnowledgeChunkPiece piece : pieces) {
      if (piece.getHeadingPath() != null
          && piece.getHeadingPath().contains("第一章")
          && piece.getParentChunkIndex() != null) {
        firstChapterChild = piece;
        break;
      }
    }
    assertThat(firstChapterChild).isNotNull();
    KnowledgeChunkPiece parent = pieces.get(firstChapterChild.getParentChunkIndex());
    assertThat(parent.getHeadingPath()).isEqualTo(firstChapterChild.getHeadingPath());
    assertThat(pieces.stream()
            .filter(piece -> piece.getHeadingPath() != null
                && piece.getHeadingPath().contains("第二章")
                && piece.getParentChunkIndex() != null)
            .findFirst())
        .isEmpty();
  }

  private static KnowledgeChunkRequest request(String title, String text) {
    return KnowledgeChunkRequest.builder()
        .title(title)
        .text(text)
        .chunkProfile(KnowledgeChunkProfile.MARKDOWN_STRUCTURED)
        .params(KnowledgeChunkParams.defaults())
        .build();
  }

  private static String repeatChar(char ch, int count) {
    return String.valueOf(ch).repeat(count);
  }
}
