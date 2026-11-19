package com.chengwenfang.knowledge.facade.dto.request;

import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库文档分页列表查询。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentListRequest {
  /** 页码，从 1 开始。 */
  @Builder.Default
  private int page = 1;
  /** 每页条数。 */
  @Builder.Default
  private int pageSize = 10;
  /** 来源种类；空表示全部。 */
  private KnowledgeSourceKind sourceKind;
  /** 标题关键字。 */
  private String keyword;
  /** 介质种类；空表示全部。 */
  private KnowledgeMediaKind mediaKind;
  /** 入库日起（含）。 */
  private LocalDate createdFrom;
  /** 入库日止（含）。 */
  private LocalDate createdTo;
}
