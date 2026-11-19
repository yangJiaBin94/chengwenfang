package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import org.junit.jupiter.api.Test;

/** {@link ChunkParamsResolver} 视频 overlap。 */
class ChunkParamsResolverTest {

  @Test
  void videoGetsLargerOverlap() {
    ChunkParamsResolver resolver = new ChunkParamsResolver();
    KnowledgeChunkParams video =
        resolver.resolve(KnowledgeChunkProfile.PLAIN, KnowledgeMediaKind.VIDEO);
    KnowledgeChunkParams plain =
        resolver.resolve(KnowledgeChunkProfile.PLAIN, KnowledgeMediaKind.PLAIN_TEXT);
    assertThat(video.getOverlapChars()).isEqualTo(KnowledgeChunkConstants.VIDEO_OVERLAP_CHARS);
    assertThat(plain.getOverlapChars()).isEqualTo(KnowledgeChunkConstants.OVERLAP_CHARS);
  }
}
