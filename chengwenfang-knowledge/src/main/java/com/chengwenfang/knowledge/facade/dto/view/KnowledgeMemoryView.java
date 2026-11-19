package com.chengwenfang.knowledge.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 长期记忆条目视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeMemoryView {
  /** 主键。 */
  private String id;
  /** 正文。 */
  private String content;
  /** 出处。 */
  private String provenance;
  /** 是否暂停。 */
  private Boolean paused;
  /** 过期时间。 */
  private LocalDateTime expiresAt;
  /** 创建时间。 */
  private LocalDateTime createdAt;
}
