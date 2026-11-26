package com.chengwenfang.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 检索观测轨迹（表 {@code knowledge_retrieval_traces}）。 */
@TableName("knowledge_retrieval_traces")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRetrievalTraceEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 所属用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 会话 ID。 */
  @TableField("session_id")
  private String sessionId;
  /** 轮次。 */
  @TableField("round_no")
  private Integer roundNo;
  /** 模式。 */
  @TableField("mode_key")
  private String modeKey;
  /** 查询文本。 */
  @TableField("query_text")
  private String queryText;
  /** 扩展 JSON。 */
  @TableField("payload_json")
  private String payloadJson;
  /** 耗时毫秒。 */
  @TableField("latency_ms")
  private Integer latencyMs;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
