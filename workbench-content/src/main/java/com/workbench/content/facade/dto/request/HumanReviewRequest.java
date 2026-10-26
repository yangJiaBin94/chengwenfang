package com.workbench.content.facade.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 提交人工审核请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanReviewRequest {
  /** 状态 approved / rejected。 */
  @NotBlank(message = "不能为空")
  private String status;

  /** 评论。 */
  private String comment;

  /** 统一文档 id；空则服务端按 articleId 解析。 */
  private String documentId;
}
