package com.chengwenfang.quality.facade.dto.request;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建 pending 审稿运行请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleReviewRunCreateRequest {
  /** 统一文档 id；空则服务端派生。 */
  private String documentId;
  /** 模式。 */
  private String mode;
  /** 内容哈希。 */
  private String contentHash;
  /** 标准版本。 */
  private String standardVersion;
  /** 正文。 */
  private String content;
  /** 来源列表。 */
  private List<Map<String, Object>> sources;

  /** 从松散 Map 构造。 */
  @SuppressWarnings("unchecked")
  public static HotArticleReviewRunCreateRequest fromMap(Map<String, Object> input) {
    Map<String, Object> src = input == null ? Map.of() : input;
    List<Map<String, Object>> sources = src.get("sources") instanceof List<?> list
        ? (List<Map<String, Object>>) (List<?>) list
        : null;
    return HotArticleReviewRunCreateRequest.builder()
        .documentId(src.get("documentId") == null ? null : String.valueOf(src.get("documentId")))
        .mode(src.get("mode") == null ? null : String.valueOf(src.get("mode")))
        .contentHash(src.get("contentHash") == null ? null : String.valueOf(src.get("contentHash")))
        .standardVersion(src.get("standardVersion") == null ? null : String.valueOf(src.get("standardVersion")))
        .content(src.get("content") == null ? null : String.valueOf(src.get("content")))
        .sources(sources)
        .build();
  }
}
