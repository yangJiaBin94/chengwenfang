package com.chengwenfang.content.facade.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主轨产出正文版本追加成功后发布，供知识库等域联动重建索引。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOutputContentChangedEvent {
  /** 产出 ID。 */
  private String outputId;
  /** 作品 ID。 */
  private String workId;
  /** 渠道 code。 */
  private String channelCode;
  /** 新版本号。 */
  private Integer versionNo;
  /** 正文哈希。 */
  private String contentHash;
}
