package com.chengwenfang.knowledge.domain.model;

import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 切块请求：归一化正文 + 形态 + 介质 + 滑窗参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkRequest {

  /** 文档标题（写入 embedding 前缀）。 */
  private String title;
  /** 归一化正文（charSpan 相对此字符串）。 */
  private String text;
  /** 介质种类（可为 null；影响参数解析结果时应已写入 params）。 */
  private KnowledgeMediaKind mediaKind;
  /** 切块形态。 */
  private KnowledgeChunkProfile chunkProfile;
  /** 滑窗参数。 */
  private KnowledgeChunkParams params;
}
