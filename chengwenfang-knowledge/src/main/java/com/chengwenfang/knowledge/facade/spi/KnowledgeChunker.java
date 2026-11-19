package com.chengwenfang.knowledge.facade.spi;

import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkRequest;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import java.util.List;

/**
 * 知识库切块策略：按 {@link KnowledgeChunkProfile} 注册，索引管线按形态路由。
 */
public interface KnowledgeChunker {

  /**
   * 是否支持该切块形态。
   *
   * @param chunkProfile 切块形态
   * @return 支持时 true
   */
  boolean supports(KnowledgeChunkProfile chunkProfile);

  /**
   * 将归一化正文切成检索块。
   *
   * @param request 切块请求
   * @return 切块列表（含 charSpan / embeddingText）
   */
  List<KnowledgeChunkPiece> chunk(KnowledgeChunkRequest request);
}
