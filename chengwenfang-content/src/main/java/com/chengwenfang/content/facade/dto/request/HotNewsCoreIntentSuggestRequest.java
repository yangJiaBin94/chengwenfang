package com.chengwenfang.content.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题主要意图 AI 推导请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsCoreIntentSuggestRequest {
  /** 主题名称。 */
  private String name;
  /** 主题说明 / 用户想要什么。 */
  private String description;
  /** 领域标签文案。 */
  private String domainLabel;
}
