package com.workbench.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 启动文章生成运行请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsGenerationRunRequest {
  /** 统一文档 id（公众号轨）；空则服务端派生。 */
  private String documentId;
  /** 范围：full / draft / wechat / images / audit */
  private String scope;
  /** 字数选项 */
  private String wordCountOption;
  /** 配图数量选项 */
  private String imageCountOption;
}
