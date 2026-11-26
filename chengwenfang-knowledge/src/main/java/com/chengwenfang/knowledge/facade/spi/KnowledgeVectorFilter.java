package com.chengwenfang.knowledge.facade.spi;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向量检索可选过滤条件（字段为空表示不限制）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVectorFilter {
  /** 限定文档 ID。 */
  private String documentId;
  /** 限定所属用户；空表示不限（仅管理员全库检索）。 */
  private String ownerUserId;
  /** 限定来源类型（单值；与 {@link #sourceKinds} 并存时优先列表）。 */
  private String sourceKind;
  /** 限定来源类型列表（多选 OR）。 */
  private List<String> sourceKinds;
  /** 限定媒体类型（单值；与 {@link #mediaKinds} 并存时优先列表）。 */
  private String mediaKind;
  /** 限定媒体类型列表（多选 OR）。 */
  private List<String> mediaKinds;
  /** 限定主题 ID。 */
  private String themeId;
  /** 限定索引版本（活跃读版本）。 */
  private String indexVersion;
  /** 限定嵌入模型名（防混模）。 */
  private String embeddingModel;
}
