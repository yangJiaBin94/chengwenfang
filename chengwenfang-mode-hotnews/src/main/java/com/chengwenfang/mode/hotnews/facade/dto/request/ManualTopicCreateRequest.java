package com.chengwenfang.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点雷达手动添加选题请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualTopicCreateRequest {
  /** 选题标题（必填）。 */
  private String title;
  /** 摘要/速览（可选）。 */
  private String summary;
  /** 原文链接（可选，http/https）。 */
  private String url;
}
