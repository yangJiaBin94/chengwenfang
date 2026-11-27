package com.chengwenfang.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库索引活跃版本（表 {@code knowledge_index_state}，每用户一行）。 */
@TableName("knowledge_index_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIndexStateEntity {
  /** 所属用户 ID（主键）。 */
  @TableId("owner_user_id")
  private String ownerUserId;
  /** 当前读路径版本。 */
  @TableField("active_version")
  private String activeVersion;
  /** 全量重建进行中的写入版本；空表示无进行中的蓝绿写。 */
  @TableField("writing_version")
  private String writingVersion;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
