package com.chengwenfang.knowledge.domain.rule;

import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import org.springframework.stereotype.Component;

/**
 * 按切块形态与介质解析滑窗参数。
 */
@Component
public class ChunkParamsResolver {

  /**
   * 解析切块参数。
   *
   * @param chunkProfile 切块形态（可为 null，按默认）
   * @param mediaKind    介质（视频转写提高 overlap）
   * @return 参数
   */
  public KnowledgeChunkParams resolve(
      KnowledgeChunkProfile chunkProfile, KnowledgeMediaKind mediaKind) {
    KnowledgeChunkParams params = KnowledgeChunkParams.defaults();
    // 口播转写句界更碎，加大重叠保留跨句上下文
    if (KnowledgeMediaKind.VIDEO == mediaKind) {
      params.setOverlapChars(KnowledgeChunkConstants.VIDEO_OVERLAP_CHARS);
    }
    // Table / Slide 结构优先；滑窗参数仅用于页内窗或回退 Plain（由各 Chunker 决定是否调用）
    return params;
  }
}
