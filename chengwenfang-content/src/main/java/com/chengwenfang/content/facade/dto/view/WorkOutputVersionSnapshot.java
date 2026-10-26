package com.chengwenfang.content.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 产出版本快照（跨域只读投影）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOutputVersionSnapshot {
  /** 版本行 id。 */
  private String id;
  /** 产出 id。 */
  private String outputId;
  /** 版本号。 */
  private int versionNo;
  /** 正文。 */
  private String bodyMd;
  /** 哈希。 */
  private String contentHash;
  /** 来源。 */
  private String source;
  /** 原因。 */
  private String reason;
  /** 工作流运行 id。 */
  private String workflowRunId;
  /** 创建时间。 */
  private LocalDateTime createdAt;
}
