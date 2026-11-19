package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkRequest;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link PlainTextChunker} 与视频 overlap。 */
class PlainTextChunkerTest {

  @Test
  void shortTextIsSingleChunk() {
    PlainTextChunker chunker = new PlainTextChunker();
    List<KnowledgeChunkPiece> pieces = chunker.chunk(request(
        "图",
        "这是一段短图注，不超过上限。",
        KnowledgeChunkParams.defaults()));
    assertThat(pieces).hasSize(1);
    assertThat(pieces.get(0).getCharSpanStart()).isNotNull();
    assertThat(pieces.get(0).getCharSpanEnd()).isGreaterThan(0);
  }

  @Test
  void videoOverlapUses120() {
    String longText = ("口播句子。".repeat(200));
    KnowledgeChunkParams params = KnowledgeChunkParams.defaults();
    params.setOverlapChars(KnowledgeChunkConstants.VIDEO_OVERLAP_CHARS);
    PlainTextChunker chunker = new PlainTextChunker();
    List<KnowledgeChunkPiece> pieces = chunker.chunk(
        KnowledgeChunkRequest.builder()
            .title("视频")
            .text(longText)
            .mediaKind(KnowledgeMediaKind.VIDEO)
            .chunkProfile(KnowledgeChunkProfile.PLAIN)
            .params(params)
            .build());
    assertThat(pieces.size()).isGreaterThan(1);
    boolean overlapFound = false;
    for (int index = 1; index < pieces.size(); index++) {
      String previous = pieces.get(index - 1).getContent();
      String current = pieces.get(index).getContent();
      if (previous.length() >= 40 && current.length() >= 40) {
        String tail = previous.substring(previous.length() - 40);
        if (current.contains(tail)) {
          overlapFound = true;
          break;
        }
      }
    }
    assertThat(overlapFound).isTrue();
  }

  private static KnowledgeChunkRequest request(
      String title, String text, KnowledgeChunkParams params) {
    return KnowledgeChunkRequest.builder()
        .title(title)
        .text(text)
        .chunkProfile(KnowledgeChunkProfile.PLAIN)
        .params(params)
        .build();
  }
}
