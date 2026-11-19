package com.chengwenfang.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图边纠错旁路（表 {@code knowledge_graph_edge_overrides}）。 */
@TableName("knowledge_graph_edge_overrides")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGraphEdgeOverrideEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** Neo4j 边业务 UID。 */
  @TableField("neo4j_edge_uid")
  private String neo4jEdgeUid;
  /** 来源切块。 */
  @TableField("source_chunk_id")
  private String sourceChunkId;
  /** 是否启用。 */
  @TableField("enabled")
  private Boolean enabled;
  /** 置信度。 */
  @TableField("confidence")
  private Double confidence;
  /** 纠错说明。 */
  @TableField("note")
  private String note;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
