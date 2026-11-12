package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
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
    List<KnowledgeChunkPiece> pieces = chunker.chunk("测试文档", markdown);

    assertThat(pieces).hasSizeGreaterThan(1);
    assertThat(pieces).anyMatch(piece -> piece.getHeadingPath().contains("第一章"));
    assertThat(pieces).anyMatch(piece -> piece.getHeadingPath().contains("第二章"));

    boolean overlapFound = false;
    for (int index = 1; index < pieces.size(); index++) {
      String previous = pieces.get(index - 1).getContent();
      String current = pieces.get(index).getContent();
      if (previous.length() >= 80 && current.length() >= 80) {
        String tail = previous.substring(previous.length() - 80);
        if (current.contains(tail)) {
          overlapFound = true;
          break;
        }
      }
    }
    assertThat(overlapFound).as("相邻块应存在约 80 字符重叠").isTrue();
  }

  /**
   * 重复字符构造长文本。
   *
   * @param ch    字符
   * @param count 次数
   * @return 字符串
   */
  private static String repeatChar(char ch, int count) {
    return String.valueOf(ch).repeat(count);
  }
}
