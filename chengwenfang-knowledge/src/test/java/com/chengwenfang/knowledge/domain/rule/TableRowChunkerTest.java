package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkRequest;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link TableRowChunker} 一行一块。 */
class TableRowChunkerTest {

  @Test
  void chunksOneRowPerBlockWithSheetPath() {
    String body = "## 员工\n\n姓名:甲；年龄:1\n\n姓名:乙；年龄:2\n\n## 部门\n\n名称:研发";
    TableRowChunker chunker = new TableRowChunker();
    List<KnowledgeChunkPiece> pieces = chunker.chunk(request("表", body));
    assertThat(pieces).hasSize(3);
    assertThat(pieces.get(0).getHeadingPath()).isEqualTo("员工");
    assertThat(pieces.get(0).getContent()).contains("甲");
    assertThat(pieces.get(1).getHeadingPath()).isEqualTo("员工");
    assertThat(pieces.get(2).getHeadingPath()).isEqualTo("部门");
    assertThat(body.substring(pieces.get(0).getCharSpanStart(), pieces.get(0).getCharSpanEnd()))
        .isEqualTo(pieces.get(0).getContent());
    assertThat(pieces.get(0).getEmbeddingText()).contains("员工");
  }

  @Test
  void rejectsOverMaxRows() {
    StringBuilder body = new StringBuilder("## csv");
    for (int index = 0; index < KnowledgeChunkConstants.MAX_TABLE_ROWS + 1; index++) {
      body.append("\n\n列:").append(index);
    }
    TableRowChunker chunker = new TableRowChunker();
    assertThatThrownBy(() -> chunker.chunk(request("大表", body.toString())))
        .isInstanceOf(BusinessException.class)
        .satisfies(exception -> {
          BusinessException businessException = (BusinessException) exception;
          assertThat(businessException.getCode()).isEqualTo(ErrorCodes.KNOWLEDGE_TABLE_TOO_LARGE);
          assertThat(businessException.getHttpStatus()).isEqualTo(400);
          assertThat(businessException.getMessage()).contains("上限");
        });
  }

  private static KnowledgeChunkRequest request(String title, String text) {
    return KnowledgeChunkRequest.builder()
        .title(title)
        .text(text)
        .chunkProfile(KnowledgeChunkProfile.TABLE_ROWS)
        .params(KnowledgeChunkParams.defaults())
        .build();
  }
}
