package com.workbench.quality.facade.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 启动热点审稿请求（对齐前端 runHotNewsAudit）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleReviewCreateRequest {
  /** 文章 ID */
  @NotBlank(message = "不能为空")
  private String articleId;
  /** 统一文档 id（公众号轨）；空则服务端按 articleId 派生。 */
  private String documentId;
  /** 模式 */
  private String mode;
  /** 内容哈希 */
  private String contentHash;
  /** 标准版本 */
  private String standardVersion;
  /** 正文 */
  private String content;
  /** 来源列表 */
  private List<Map<String, Object>> sources;
}
