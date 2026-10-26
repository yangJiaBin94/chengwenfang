package com.workbench.publishing.facade.dto.request;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建发布快照请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishingSnapshotCreateRequest {
  /** 可选 Markdown 覆盖。 */
  private String markdown;
  /** 统一文档 ID（公众号轨）；空则仅写 article_id。 */
  private String documentId;
  /** 主题版本 ID。 */
  private String themeVersionId;
  /** 文章版本号。 */
  private Integer articleVersionNo;
  /** 布局选项。 */
  private Map<String, Object> options;
}