package com.workbench.quality.facade.dto.request;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点文章规则审稿快照入参。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleReviewSnapshotRequest {
  /** 正文。 */
  private String content;
  /** 内容哈希。 */
  private String contentHash;
  /** 来源列表。 */
  private List<Map<String, Object>> sources;
  /** 标准版本。 */
  private String standardVersion;
}